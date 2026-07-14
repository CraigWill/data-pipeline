#!/bin/bash
# 清理宿主机 Docker 中的纯时间戳 tag 镜像，每个仓库保留最新的一个
# 用法: clean_images.sh [--apply]   不加 --apply 则为 dry-run
set -e

APPLY=false
[ "$1" = "--apply" ] && APPLY=true

REPOS="flink-jobmanager flink-taskmanager monitor-backend monitor-frontend"
total=0

for repo in $REPOS; do
    keep=$(docker images --format '{{.Repository}}:{{.Tag}}' \
        | grep -E "^${repo}:[0-9]{10}$" | sed "s/^${repo}://" | sort -n | tail -1)
    [ -z "$keep" ] && continue
    del_list=$(docker images --format '{{.Repository}}:{{.Tag}}' \
        | grep -E "^${repo}:[0-9]{10}$" | grep -v "^${repo}:${keep}$")
    cnt=$(echo -n "$del_list" | grep -c . || true)
    echo "[$repo] 保留 ${repo}:${keep}，删除 ${cnt} 个"
    total=$((total + cnt))
    if [ "$APPLY" = true ] && [ -n "$del_list" ]; then
        echo "$del_list" | xargs -r docker rmi >/dev/null 2>&1 || true
    fi
done

echo "----------------------------------------"
if [ "$APPLY" = true ]; then
    echo "已删除 ${total} 个时间戳镜像"
else
    echo "[DRY-RUN] 将删除 ${total} 个时间戳镜像（加 --apply 实际执行）"
fi
