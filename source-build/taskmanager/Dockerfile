# Dockerfile for Flink TaskManager - Source Build
# Builds Apache Flink from source code using Maven, instead of downloading pre-built binaries.
# Multi-stage build:
#   Stage 1: Clone Flink source and compile with Maven
#   Stage 2: Runtime image with compiled Flink distribution

# ============================================================
# Stage 1: Build Flink from source
# ============================================================
FROM eclipse-temurin:17-jdk AS flink-builder

# Flink version to build from source
ARG FLINK_VERSION=1.20.0
ARG SCALA_VERSION=2.12
ARG FLINK_GIT_TAG=release-${FLINK_VERSION}
ARG FLINK_REPO=https://github.com/apache/flink.git

ENV MAVEN_VERSION=3.9.6 \
    MAVEN_HOME=/opt/maven \
    DEBIAN_FRONTEND=noninteractive

# Install build dependencies
RUN set -ex && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        git \
        curl \
        wget \
        ca-certificates \
        build-essential \
        libsnappy-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Maven
RUN set -ex && \
    curl -fSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz && \
    mkdir -p ${MAVEN_HOME} && \
    tar -xzf /tmp/maven.tar.gz -C ${MAVEN_HOME} --strip-components=1 && \
    rm -f /tmp/maven.tar.gz && \
    ln -s ${MAVEN_HOME}/bin/mvn /usr/bin/mvn

# Clone Flink source code
WORKDIR /build
RUN set -ex && \
    git clone --depth 1 --branch ${FLINK_GIT_TAG} ${FLINK_REPO} flink-src

# Build Flink from source (skip tests for faster build)
WORKDIR /build/flink-src
RUN set -ex && \
    mvn clean install -DskipTests \
        -Dfast \
        -Dscala-${SCALA_VERSION} \
        -Pvendor-repos \
        -pl flink-dist \
        -am \
        -T 1C \
        --no-transfer-progress && \
    echo "Flink source build completed successfully"

# Extract the built distribution
RUN set -ex && \
    DIST_DIR=$(find /build/flink-src/flink-dist/target -maxdepth 1 -name "flink-${FLINK_VERSION}-bin" -type d | head -1) && \
    if [ -z "$DIST_DIR" ]; then \
        DIST_DIR=$(find /build/flink-src/flink-dist/target -maxdepth 1 -name "flink-*-bin" -type d | head -1); \
    fi && \
    if [ -z "$DIST_DIR" ]; then \
        echo "Looking for distribution archive..." && \
        DIST_TAR=$(find /build/flink-src/flink-dist/target -maxdepth 1 -name "flink-*.tgz" | head -1) && \
        mkdir -p /build/flink-dist && \
        tar -xzf "$DIST_TAR" -C /build/flink-dist --strip-components=1; \
    else \
        FLINK_DIR=$(find "$DIST_DIR" -maxdepth 1 -name "flink-*" -type d | head -1) && \
        if [ -n "$FLINK_DIR" ]; then \
            cp -r "$FLINK_DIR" /build/flink-dist; \
        else \
            cp -r "$DIST_DIR" /build/flink-dist; \
        fi; \
    fi && \
    ls -la /build/flink-dist/ && \
    echo "Distribution extracted to /build/flink-dist"

# ============================================================
# Stage 2: Runtime image
# ============================================================
FROM eclipse-temurin:17-jre

ENV FLINK_VERSION=1.20.0 \
    SCALA_VERSION=2.12 \
    FLINK_HOME=/opt/flink \
    FLINK_USER=flink \
    FLINK_UID=9999 \
    FLINK_GID=9999 \
    DEBIAN_FRONTEND=noninteractive

WORKDIR /opt/flink

# Install runtime dependencies
RUN set -ex && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        bash \
        curl \
        netcat-openbsd \
        ca-certificates \
        procps \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Create flink user and group
RUN groupadd -r -g ${FLINK_GID} ${FLINK_USER} && \
    useradd -r -u ${FLINK_UID} -g ${FLINK_GID} -m -d /home/${FLINK_USER} -s /bin/bash ${FLINK_USER}

# Copy the compiled Flink distribution from builder stage
COPY --from=flink-builder /build/flink-dist/ /opt/flink/

# Create necessary directories
RUN mkdir -p /opt/flink/checkpoints && \
    mkdir -p /opt/flink/savepoints && \
    mkdir -p /opt/flink/logs && \
    mkdir -p /opt/flink/data && \
    mkdir -p /opt/flink/usrlib && \
    chown -R ${FLINK_USER}:${FLINK_USER} /opt/flink

# Download Oracle JDBC driver
RUN set -ex && \
    curl -L https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/19.3.0.0/ojdbc8-19.3.0.0.jar \
        -o /opt/flink/lib/ojdbc8.jar && \
    chown ${FLINK_USER}:${FLINK_USER} /opt/flink/lib/ojdbc8.jar

# Copy application JAR
COPY --chown=${FLINK_USER}:${FLINK_USER} flink-jobs/target/flink-jobs-*.jar /opt/flink/usrlib/flink-jobs.jar

# Copy Flink configuration
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/taskmanager/flink-conf.yaml /opt/flink/conf/flink-conf.yaml

# Copy Log4j configuration
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/taskmanager/log4j.properties /opt/flink/conf/log4j.properties

# Copy custom entrypoint script
COPY --chown=${FLINK_USER}:${FLINK_USER} docker/taskmanager/entrypoint.sh /opt/flink/bin/custom-entrypoint.sh
RUN chmod +x /opt/flink/bin/custom-entrypoint.sh

# Switch to flink user
USER ${FLINK_USER}

# Expose TaskManager ports
# 6121: Data exchange port
# 6122: RPC port
# 8080: Metrics port
# 9249: Prometheus metrics port
EXPOSE 6121 6122 8080 9249

ENTRYPOINT ["/opt/flink/bin/custom-entrypoint.sh"]
