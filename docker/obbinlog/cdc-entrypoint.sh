#!/bin/bash
set -e

DEPLOY_PATH="/home/ds/oblogproxy"

# 安装 obbinlog RPM（如果还没安装）
if [ ! -f "${DEPLOY_PATH}/bin/logproxy" ]; then
    echo "Installing obbinlog-ce RPM..."
    rpm -ivh --replacefiles /app/obbinlog-ce.rpm 2>/dev/null || true
fi

cd ${DEPLOY_PATH}
echo "DEPLOY_PATH : ${DEPLOY_PATH}"

# 设置 LD_LIBRARY_PATH
LIB_PATH=${DEPLOY_PATH}/deps/lib
export LD_LIBRARY_PATH=${LIB_PATH}:${LD_LIBRARY_PATH}
chmod u+x ./bin/logproxy

# 修复 libstdc++ 版本（镜像自带的系统 libstdc++ 过旧）
if [ -f "${DEPLOY_PATH}/deps/lib/libstdc++.so.6.0.28" ]; then
    cp ${DEPLOY_PATH}/deps/lib/libstdc++.so.6.0.28 /usr/lib64/
    cd /usr/lib64 && ln -sf libstdc++.so.6.0.28 libstdc++.so.6
    cd ${DEPLOY_PATH}
    echo "Fixed libstdc++ to 6.0.28"
fi

# 配置 sys 用户凭据
if [ -n "${OB_SYS_USERNAME}" ] && [ -n "${OB_SYS_PASSWORD}" ]; then
    echo "Configuring OB sys credentials..."
    username_x=$(./bin/logproxy -x "${OB_SYS_USERNAME}" 2>/dev/null || echo "")
    password_x=$(./bin/logproxy -x "${OB_SYS_PASSWORD}" 2>/dev/null || echo "")

    if [ -n "${username_x}" ] && [ -n "${password_x}" ]; then
        sed -i "s/\"ob_sys_username\"[[:space:]]*:[[:space:]]*\"[^\"]*\"/\"ob_sys_username\": \"${username_x}\"/" ./conf/conf.json
        sed -i "s/\"ob_sys_password\"[[:space:]]*:[[:space:]]*\"[^\"]*\"/\"ob_sys_password\": \"${password_x}\"/" ./conf/conf.json
        echo "Credentials configured."
    else
        echo "WARNING: logproxy -x failed, skipping credential encryption"
    fi
fi

# 创建运行目录
mkdir -p ./run ./log

echo "Starting logproxy (obbinlog-ce CDC mode) in foreground..."
exec ./bin/logproxy -f ./conf/conf.json
