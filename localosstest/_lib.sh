#!/usr/bin/env bash
# localosstest 公共函数：Windows (Git Bash / MSYS / Cygwin) + Unix
# shellcheck disable=SC2034

is_windows() {
    case "$(uname -s 2>/dev/null || echo unknown)" in
        MINGW*|MSYS*|CYGWIN*|Windows_NT*) return 0 ;;
        *) return 1 ;;
    esac
}

# 把 Git Bash 路径转成 Flink/Java 更认的形式（Windows: C:/...；Unix: 原样）
to_flink_path() {
    local p="${1-}"
    if [ -z "$p" ]; then
        printf '%s' "$p"
        return
    fi
    if is_windows && command -v cygpath >/dev/null 2>&1; then
        # -m: mixed，正斜杠，避免 YAML/Java 被反斜杠转义折腾
        cygpath -m "$p"
        return
    fi
    # 无 cygpath 时兜底：/c/Users/... → C:/Users/...
    if is_windows && [[ "$p" =~ ^/([a-zA-Z])/(.*)$ ]]; then
        local drive
        drive="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:lower:]' '[:upper:]')"
        printf '%s:/%s' "$drive" "${BASH_REMATCH[2]}"
        return
    fi
    printf '%s' "$p"
}

normalize_flink_home() {
    : "${FLINK_HOME:?}"
    # 去掉末尾斜杠；Windows 反斜杠统一一下便于 bash 拼接
    FLINK_HOME="${FLINK_HOME%/}"
    FLINK_HOME="${FLINK_HOME%\\}"
    if is_windows && command -v cygpath >/dev/null 2>&1; then
        # bash 内访问用 Unix 风格，避免空格/盘符问题
        FLINK_HOME="$(cygpath -u "$FLINK_HOME")"
    fi
    export FLINK_HOME
}

flink_cli() {
    if is_windows && [ -f "$FLINK_HOME/bin/flink.bat" ]; then
        printf '%s' "$FLINK_HOME/bin/flink.bat"
    else
        printf '%s' "$FLINK_HOME/bin/flink"
    fi
}

start_cluster_script() {
    if is_windows && [ -f "$FLINK_HOME/bin/start-cluster.bat" ]; then
        printf '%s' "$FLINK_HOME/bin/start-cluster.bat"
    else
        printf '%s' "$FLINK_HOME/bin/start-cluster.sh"
    fi
}

stop_cluster_script() {
    if is_windows && [ -f "$FLINK_HOME/bin/stop-cluster.bat" ]; then
        printf '%s' "$FLINK_HOME/bin/stop-cluster.bat"
    else
        printf '%s' "$FLINK_HOME/bin/stop-cluster.sh"
    fi
}

# 在 Windows 上必须用 .bat：Git Bash 跑 .sh 会用 ':' 拼 classpath，Windows JVM 需要 ';'，
# 表现为 org.apache.flink.* ClassNotFoundException。
run_flink_script() {
    local script="$1"
    shift
    if [[ "$script" == *.bat ]]; then
        local win_script win_home win_conf win_log win_pid
        win_script="$(to_flink_path "$script")"
        win_home="$(to_flink_path "$FLINK_HOME")"
        win_conf="$(to_flink_path "${FLINK_CONF_DIR:-}")"
        win_log="$(to_flink_path "${FLINK_LOG_DIR:-}")"
        win_pid="$(to_flink_path "${FLINK_PID_DIR:-}")"
        # bat 继承的环境变量必须是 Windows 路径；Git Bash 的 /c/... 会导致找不到 lib → ClassNotFound
        MSYS2_ARG_CONV_EXCL='*' cmd.exe //C \
            "set \"FLINK_HOME=${win_home}\"&& set \"FLINK_CONF_DIR=${win_conf}\"&& set \"FLINK_LOG_DIR=${win_log}\"&& set \"FLINK_PID_DIR=${win_pid}\"&& \"${win_script}\" $*"
    else
        "$script" "$@"
    fi
}

run_flink_cli() {
    local cli
    cli="$(flink_cli)"
    if [[ "$cli" == *.bat ]]; then
        local win_cli win_home win_conf win_log win_pid
        win_cli="$(to_flink_path "$cli")"
        win_home="$(to_flink_path "$FLINK_HOME")"
        win_conf="$(to_flink_path "${FLINK_CONF_DIR:-}")"
        win_log="$(to_flink_path "${FLINK_LOG_DIR:-}")"
        win_pid="$(to_flink_path "${FLINK_PID_DIR:-}")"
        # 将参数拼进 cmd 一行；路径类参数应已由调用方 to_flink_path
        MSYS2_ARG_CONV_EXCL='*' cmd.exe //C \
            "set \"FLINK_HOME=${win_home}\"&& set \"FLINK_CONF_DIR=${win_conf}\"&& set \"FLINK_LOG_DIR=${win_log}\"&& set \"FLINK_PID_DIR=${win_pid}\"&& \"${win_cli}\" $*"
    else
        "$cli" "$@"
    fi
}

# 安装 OSS 插件到 $FLINK_HOME/plugins/oss-fs-hadoop
# Windows 上不用软链（常失败或变成文本文件），改为目录拷贝。
install_oss_plugin_link_or_copy() {
    local src_dir="$1"
    local dest_dir="$FLINK_HOME/plugins/oss-fs-hadoop"
    mkdir -p "$FLINK_HOME/plugins"
    rm -rf "$dest_dir"
    if is_windows; then
        mkdir -p "$dest_dir"
        cp -f "$src_dir"/*.jar "$dest_dir/" 2>/dev/null || true
        # 若 cp 通配失败，逐个拷
        if ! ls "$dest_dir"/*.jar >/dev/null 2>&1; then
            local f
            for f in "$src_dir"/*; do
                [ -f "$f" ] || continue
                cp -f "$f" "$dest_dir/"
            done
        fi
        echo "plugin installed by COPY -> $dest_dir"
    else
        ln -sfn "$src_dir" "$dest_dir"
        echo "plugin installed by SYMLINK -> $dest_dir -> $src_dir"
    fi
    if ! ls "$dest_dir"/flink-oss-fs-hadoop-*.jar >/dev/null 2>&1; then
        echo "ERROR: flink-oss-fs-hadoop jar missing under $dest_dir" >&2
        return 1
    fi
}
