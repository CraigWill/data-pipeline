#!/bin/bash
# 密码加密工具
# 用于加密 .env 文件中的数据库密码
#
# 使用方法:
#   ./encrypt-password.sh "your-plain-password"
#   ./encrypt-password.sh              # 交互式输入（推荐，密码不回显）
#
# 支持密码中包含任意特殊字符：| & ; $ ` ! 等

set -e

# ── 读取密码 ──────────────────────────────────────────────────────
if [ $# -ge 1 ]; then
    # 命令行参数传入（双引号保护，| 等特殊字符安全）
    PLAIN_PASSWORD="$1"
else
    # 交互式输入：不回显，支持所有特殊字符
    echo -n "请输入要加密的密码: "
    IFS= read -rs PLAIN_PASSWORD
    echo   # 换行
    if [ -z "$PLAIN_PASSWORD" ]; then
        echo "错误: 密码不能为空" >&2
        exit 1
    fi
fi

# ── 读取 AES 密钥 ─────────────────────────────────────────────────
if [ -z "$AES_ENCRYPTION_KEY" ]; then
    if [ -f .env ]; then
        AES_ENCRYPTION_KEY=$(grep "^AES_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)
    fi
    if [ -z "$AES_ENCRYPTION_KEY" ]; then
        echo "错误: 未找到 AES_ENCRYPTION_KEY" >&2
        echo "请在 .env 文件中设置 AES_ENCRYPTION_KEY，或通过环境变量传入" >&2
        exit 1
    fi
fi

echo "=========================================="
echo "密码加密工具"
echo "=========================================="
echo ""

# ── Java 加密（密码通过 stdin 传入，避免命令行参数泄露和特殊字符问题）──
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 java，请先安装 JDK" >&2
    exit 1
fi

TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

cat > "$TEMP_DIR/PasswordEncryptor.java" << 'EOFJ'
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordEncryptor {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java PasswordEncryptor <base64-key>");
            System.exit(1);
        }

        String key = args[0];

        // 从 stdin 读取密码，支持任意特殊字符（包括 |、&、;、$ 等）
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = reader.readLine();
        if (password == null) {
            System.err.println("错误: stdin 无输入");
            System.exit(1);
        }

        byte[] keyBytes = Base64.getDecoder().decode(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        System.out.println(Base64.getEncoder().encodeToString(encrypted));
    }
}
EOFJ

if ! javac "$TEMP_DIR/PasswordEncryptor.java" 2>/dev/null; then
    echo "错误: Java 编译失败，请确保已安装 JDK（非仅 JRE）" >&2
    exit 1
fi

# 通过 printf + 管道将密码写入 stdin，不经过命令行参数
# printf %s 不添加换行，printf '%s\n' 添加换行（readLine 需要换行符）
ENCRYPTED=$(printf '%s\n' "$PLAIN_PASSWORD" | java -cp "$TEMP_DIR" PasswordEncryptor "$AES_ENCRYPTION_KEY" 2>/dev/null)

if [ -z "$ENCRYPTED" ]; then
    echo "错误: 加密失败" >&2
    exit 1
fi

echo "✓ 加密成功！"
echo ""
echo "原始密码: $PLAIN_PASSWORD"
echo "加密密码: $ENCRYPTED"
echo ""
echo "请将以下内容复制到 .env 或 flink-secrets.yaml 中:"
echo "  DATABASE_PASSWORD=$ENCRYPTED"
echo "  CDC_ADMIN_PASSWORD=$ENCRYPTED"
echo ""
