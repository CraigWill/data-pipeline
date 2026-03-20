#!/bin/bash
# 清理 in-progress 文件，将超过5分钟未修改的文件重命名为 csv

echo "查找超过5分钟未修改的 in-progress 文件..."

find output/cdc -name "*.inprogress*" -mmin +5 | while read file; do
    # 提取目标文件名
    dir=$(dirname "$file")
    base=$(basename "$file")
    # 移除 .inprogress.xxx 后缀和开头的点
    csv_name=$(echo "$base" | sed 's/^\.//' | sed 's/\.inprogress\.[^.]*$//')
    target="$dir/$csv_name"
    
    if [ -f "$target" ]; then
        echo "目标文件已存在，追加内容: $target"
        cat "$file" >> "$target"
        rm "$file"
    else
        echo "重命名: $file -> $target"
        mv "$file" "$target"
    fi
done

echo "清理完成"
