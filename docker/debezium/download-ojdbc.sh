#!/bin/bash
# 下载 Oracle JDBC 驱动

set -e

OJDBC_VERSION="21.9.0.0"
OJDBC_JAR="ojdbc8.jar"
DOWNLOAD_URL="https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc8/${OJDBC_VERSION}/${OJDBC_JAR}"

echo "正在下载 Oracle JDBC 驱动..."
echo "版本: ${OJDBC_VERSION}"
echo "URL: ${DOWNLOAD_URL}"

cd "$(dirname "$0")"

if [ -f "${OJDBC_JAR}" ]; then
    echo "文件已存在: ${OJDBC_JAR}"
    echo "如需重新下载，请先删除该文件"
    exit 0
fi

curl -L -k -o "${OJDBC_JAR}" "${DOWNLOAD_URL}"

if [ -f "${OJDBC_JAR}" ]; then
    echo "下载成功: ${OJDBC_JAR}"
    ls -lh "${OJDBC_JAR}"
else
    echo "下载失败"
    exit 1
fi
