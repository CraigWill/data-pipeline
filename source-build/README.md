# Flink Source Build

This directory contains Dockerfiles and scripts for building Apache Flink **from source code** instead of downloading pre-built binary distributions.

## Difference from Original Dockerfiles

| Aspect | Original (`docker/`) | Source Build (`source-build/`) |
|--------|---------------------|-------------------------------|
| Flink binary | Downloads pre-built `.tgz` from Apache mirrors | Clones Git repo and compiles with Maven |
| Build time | Fast (2-5 minutes) | Slow (30-60+ minutes) |
| Customization | None (official release binary) | Full control over source modifications |
| Base image (build) | N/A (single stage) | `eclipse-temurin:17-jdk` (multi-stage) |
| Base image (runtime) | `eclipse-temurin:17-jre` | `eclipse-temurin:17-jre` |
| Use case | Production deployment | Custom patches, debugging, development |

## Directory Structure

```
source-build/
├── README.md                    # This file
├── build-from-source.sh         # Build script
├── docker-compose.yml           # Docker Compose for source-built images
├── jobmanager/
│   └── Dockerfile               # JobManager source-build Dockerfile
└── taskmanager/
    └── Dockerfile               # TaskManager source-build Dockerfile
```

## Prerequisites

- Docker 20.10+ with BuildKit support
- At least 8GB RAM available for Docker (Maven build is memory-intensive)
- At least 10GB free disk space
- Internet access to clone from GitHub and download Maven dependencies
- Application JAR built: `mvn clean package -DskipTests` (from project root)

## Quick Start

### Build Images

```bash
# From the project root directory:
./source-build/build-from-source.sh
```

This will:
1. Build application code (if JAR not found)
2. Build JobManager image from Flink source
3. Build TaskManager image from Flink source

### Run with Docker Compose

```bash
# Start all services
docker-compose -f source-build/docker-compose.yml up -d

# Scale TaskManagers
docker-compose -f source-build/docker-compose.yml up -d --scale taskmanager=3

# View logs
docker-compose -f source-build/docker-compose.yml logs -f

# Stop all services
docker-compose -f source-build/docker-compose.yml down
```

## Customization

### Build a Different Flink Version

```bash
FLINK_VERSION=1.19.0 FLINK_GIT_TAG=release-1.19.0 ./source-build/build-from-source.sh
```

### Use a Custom Fork

```bash
FLINK_REPO=https://github.com/your-fork/flink.git \
FLINK_GIT_TAG=your-branch \
./source-build/build-from-source.sh
```

### Apply Custom Patches

To apply patches to the Flink source before building, you can modify the Dockerfile's build stage. Add a `COPY` instruction before the `mvn` build step:

```dockerfile
# In stage 1, after the git clone:
COPY patches/ /build/flink-src/patches/
RUN cd /build/flink-src && git apply patches/*.patch
```

### Build Args

| Arg | Default | Description |
|-----|---------|-------------|
| `FLINK_VERSION` | `1.20.0` | Flink version identifier |
| `SCALA_VERSION` | `2.12` | Scala version for Flink modules |
| `FLINK_GIT_TAG` | `release-1.20.0` | Git tag/branch to checkout |
| `FLINK_REPO` | `https://github.com/apache/flink.git` | Git repository URL |

## Performance Tips

1. **Docker BuildKit cache**: Ensure BuildKit is enabled (`DOCKER_BUILDKIT=1`) for better layer caching
2. **Maven cache**: The Maven dependencies are cached within the Docker build layer. Subsequent builds with the same version will be faster
3. **Parallel builds**: The build uses `-T 1C` (one thread per CPU core) for Maven
4. **Shallow clone**: Uses `--depth 1` for faster git clone

## Troubleshooting

### Build fails with OOM
Increase Docker memory limit to at least 8GB in Docker Desktop settings.

### Build is extremely slow
- Check available CPU cores (Maven uses `-T 1C` parallelism)
- Check network connectivity to Maven Central
- Consider using a Maven mirror by adding a `settings.xml` to the build stage

### Distribution extraction fails
The build script handles multiple output formats from `flink-dist`. If it still fails, check:
```bash
docker build --target flink-builder -f source-build/jobmanager/Dockerfile .
docker run --rm -it <builder-image> ls -la /build/flink-src/flink-dist/target/
```
