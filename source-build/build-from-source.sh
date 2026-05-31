#!/bin/bash
# Flink Source Build Script
# Builds Flink JobManager and TaskManager Docker images from source code
# instead of downloading pre-built binaries.
#
# Usage:
#   ./source-build/build-from-source.sh [IMAGE_TAG] [REGISTRY]
#
# Examples:
#   ./source-build/build-from-source.sh                    # builds with tag "source-latest"
#   ./source-build/build-from-source.sh v1.20.0            # builds with tag "v1.20.0"
#   ./source-build/build-from-source.sh v1.20.0 myregistry # builds with registry prefix
#
# Build Args (can be overridden via environment variables):
#   FLINK_VERSION     - Flink version to build (default: 1.20.0)
#   SCALA_VERSION     - Scala version (default: 2.12)
#   FLINK_GIT_TAG     - Git tag to checkout (default: release-${FLINK_VERSION})
#   FLINK_REPO        - Git repository URL (default: https://github.com/apache/flink.git)
#
# Note: Building from source takes significantly longer than binary builds (30-60+ minutes)
#       depending on machine resources and network speed.

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project root directory (script is in source-build/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo -e "${GREEN}=== Flink Source Build Script ===${NC}"
echo -e "${BLUE}Building Flink from source code (not pre-built binaries)${NC}"
echo ""

# Build parameters
FLINK_VERSION=${FLINK_VERSION:-1.20.0}
SCALA_VERSION=${SCALA_VERSION:-2.12}
FLINK_GIT_TAG=${FLINK_GIT_TAG:-release-${FLINK_VERSION}}
FLINK_REPO=${FLINK_REPO:-https://github.com/apache/flink.git}

# Image parameters
IMAGE_TAG=${1:-source-latest}
REGISTRY=${2:-""}

if [ -n "$REGISTRY" ]; then
    JOBMANAGER_IMAGE="${REGISTRY}/flink-jobmanager:${IMAGE_TAG}"
    TASKMANAGER_IMAGE="${REGISTRY}/flink-taskmanager:${IMAGE_TAG}"
else
    JOBMANAGER_IMAGE="flink-jobmanager:${IMAGE_TAG}"
    TASKMANAGER_IMAGE="flink-taskmanager:${IMAGE_TAG}"
fi

echo -e "${YELLOW}Build Configuration:${NC}"
echo -e "  Flink Version:    ${FLINK_VERSION}"
echo -e "  Scala Version:    ${SCALA_VERSION}"
echo -e "  Git Tag:          ${FLINK_GIT_TAG}"
echo -e "  Git Repository:   ${FLINK_REPO}"
echo -e "  Image Tag:        ${IMAGE_TAG}"
echo -e "  JobManager Image: ${JOBMANAGER_IMAGE}"
echo -e "  TaskManager Image: ${TASKMANAGER_IMAGE}"
echo ""

# Check if flink-jobs JAR exists (application code)
if [ ! -f "${PROJECT_ROOT}/flink-jobs/target/flink-jobs-1.0.0-SNAPSHOT.jar" ]; then
    echo -e "${YELLOW}Flink Jobs JAR not found. Building application code first...${NC}"
    cd "${PROJECT_ROOT}"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}Maven build of application code failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}Application code build successful${NC}"
else
    echo -e "${GREEN}Found Flink Jobs JAR, skipping application build${NC}"
fi

# Build JobManager image from source
echo ""
echo -e "${GREEN}=== Building JobManager Image (from source) ===${NC}"
echo -e "${YELLOW}WARNING: This may take 30-60+ minutes for the first build${NC}"
echo ""

cd "${PROJECT_ROOT}"
docker build \
    -f source-build/jobmanager/Dockerfile \
    --build-arg FLINK_VERSION=${FLINK_VERSION} \
    --build-arg SCALA_VERSION=${SCALA_VERSION} \
    --build-arg FLINK_GIT_TAG=${FLINK_GIT_TAG} \
    --build-arg FLINK_REPO=${FLINK_REPO} \
    -t ${JOBMANAGER_IMAGE} \
    .

if [ $? -ne 0 ]; then
    echo -e "${RED}JobManager source build failed${NC}"
    exit 1
fi
echo -e "${GREEN}JobManager image built successfully: ${JOBMANAGER_IMAGE}${NC}"

# Build TaskManager image from source
echo ""
echo -e "${GREEN}=== Building TaskManager Image (from source) ===${NC}"
echo ""

docker build \
    -f source-build/taskmanager/Dockerfile \
    --build-arg FLINK_VERSION=${FLINK_VERSION} \
    --build-arg SCALA_VERSION=${SCALA_VERSION} \
    --build-arg FLINK_GIT_TAG=${FLINK_GIT_TAG} \
    --build-arg FLINK_REPO=${FLINK_REPO} \
    -t ${TASKMANAGER_IMAGE} \
    .

if [ $? -ne 0 ]; then
    echo -e "${RED}TaskManager source build failed${NC}"
    exit 1
fi
echo -e "${GREEN}TaskManager image built successfully: ${TASKMANAGER_IMAGE}${NC}"

# Display results
echo ""
echo -e "${GREEN}=== Source Build Complete ===${NC}"
docker images | grep -E "REPOSITORY|flink-(jobmanager|taskmanager)" | grep -E "REPOSITORY|${IMAGE_TAG}"

echo ""
echo -e "${GREEN}=== Usage ===${NC}"
echo "1. Run with docker-compose (source-build version):"
echo "   docker-compose -f source-build/docker-compose.yml up -d"
echo ""
echo "2. Run standalone:"
echo "   docker run -d --name jobmanager ${JOBMANAGER_IMAGE}"
echo "   docker run -d --name taskmanager ${TASKMANAGER_IMAGE}"
echo ""
echo "3. Push to registry:"
if [ -n "$REGISTRY" ]; then
    echo "   docker push ${JOBMANAGER_IMAGE}"
    echo "   docker push ${TASKMANAGER_IMAGE}"
else
    echo "   docker tag ${JOBMANAGER_IMAGE} <registry>/flink-jobmanager:${IMAGE_TAG}"
    echo "   docker tag ${TASKMANAGER_IMAGE} <registry>/flink-taskmanager:${IMAGE_TAG}"
fi

# Optional: Push to registry
if [ -n "$REGISTRY" ]; then
    read -p "Push images to registry ${REGISTRY}? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}Pushing JobManager image...${NC}"
        docker push ${JOBMANAGER_IMAGE}
        echo -e "${YELLOW}Pushing TaskManager image...${NC}"
        docker push ${TASKMANAGER_IMAGE}
        echo -e "${GREEN}Images pushed successfully${NC}"
    fi
fi
