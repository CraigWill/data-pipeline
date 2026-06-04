#!/bin/bash
set -e

DEPLOY_PATH="/home/ds/oblogproxy"
cd ${DEPLOY_PATH}

echo "DEPLOY_PATH : ${DEPLOY_PATH}"

LIB_PATH=${DEPLOY_PATH}/deps/lib
export LD_LIBRARY_PATH=${LIB_PATH}:${LD_LIBRARY_PATH}
chmod u+x ./bin/logproxy

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
        echo "WARNING: logproxy -x failed, using defaults"
    fi
fi

# 创建必要目录
mkdir -p ./run ./log

# 前台运行 logproxy（不用 run.sh 的后台方式）
echo "Starting logproxy in foreground..."
exec ./bin/logproxy -f ./conf/conf.json
