#!/bin/bash
# Script to build all Docker images for the realtime data pipeline
# 需求: 8.1, 8.2, 8.3, 8.4

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
VERSION=${VERSION:-1.0.0}
REGISTRY=${REGISTRY:-}
PROJECT_NAME="realtime-pipeline"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Building Realtime Data Pipeline Images${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if JAR file exists
echo -e "${YELLOW}Checking for application JAR...${NC}"
JAR_FILE=$(ls target/realtime-data-pipeline-*.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}ERROR: Application JAR not found in target/ directory${NC}"
    echo -e "${YELLOW}Please run 'mvn clean package' first${NC}"
    exit 1
fi

echo -e "${GREEN}Found JAR: $JAR_FILE${NC}"
echo ""

# Function to build an image
build_image() {
    local component=$1
    local dockerfile=$2
    local image_name="${PROJECT_NAME}/${component}"
    
    if [ -n "$REGISTRY" ]; then
        image_name="${REGISTRY}/${image_name}"
    fi
    
    echo -e "${YELLOW}Building ${component} image...${NC}"
    echo "  Image: ${image_name}:${VERSION}"
    echo "  Dockerfile: ${dockerfile}"
    
    docker build \
        -f "${dockerfile}" \
        -t "${image_name}:${VERSION}" \
        -t "${image_name}:latest" \
        .
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully built ${component} image${NC}"
    else
        echo -e "${RED}✗ Failed to build ${component} image${NC}"
        exit 1
    fi
    echo ""
}

# Build JobManager image
build_image "jobmanager" "docker/jobmanager/Dockerfile"

# Build TaskManager image
build_image "taskmanager" "docker/taskmanager/Dockerfile"

# Summary
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build Summary${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Built images:"
docker images | grep "${PROJECT_NAME}" | grep -E "${VERSION}|latest"
echo ""

echo -e "${GREEN}All images built successfully!${NC}"
echo ""
echo "Next steps:"
echo "  1. Test images: docker run --rm <image-name>:${VERSION}"
echo "  2. Push to registry: docker push <image-name>:${VERSION}"
echo "  3. Deploy with Docker Compose: docker-compose up -d"
echo ""
