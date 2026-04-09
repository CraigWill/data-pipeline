#!/bin/bash
# 密码加密工具
# 用于加密 .env 文件中的数据库密码

set -e

if [ $# -eq 0 ]; then
    echo "错误: 请提供要加密的密码"
    echo ""
    echo "使用方法:"
    echo "  ./encrypt-password.sh \"your-plain-password\""
    exit 1
fi

PLAIN_PASSWORD="$1"

# 检查 AES_ENCRYPTION_KEY
if [ -z "$AES_ENCRYPTION_KEY" ]; then
    if [ -f .env ]; then
        AES_ENCRYPTION_KEY=$(grep "^AES_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)
        if [ -z "$AES_ENCRYPTION_KEY" ]; then
            echo "错误: 未找到 AES_ENCRYPTION_KEY"
            echo "请先在 .env 文件中设置 AES_ENCRYPTION_KEY"
            exit 1
        fi
    else
        echo "错误: .env 文件不存在"
        exit 1
    fi
fi

echo "=========================================="
echo "密码加密工具"
echo "=========================================="
echo ""

# 使用 Java 加密
if command -v java &> /dev/null; then
    TEMP_DIR=$(mktemp -d)
    JAVA_FILE="$TEMP_DIR/PasswordEncryptor.java"
    
    cat > "$JAVA_FILE" << 'EOFJ'
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordEncryptor {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java PasswordEncryptor <key> <password>");
            System.exit(1);
        }
        
        String key = args[0];
        String password = args[1];
        
        try {
            // Base64 解码密钥（密钥是 Base64 编码的）
            byte[] keyBytes = Base64.getDecoder().decode(key);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            String encryptedPassword = Base64.getEncoder().encodeToString(encrypted);
            System.out.println(encryptedPassword);
        } catch (Exception e) {
            System.err.println("加密失败: " + e.getMessage());
            System.exit(1);
        }
    }
}
EOFJ
    
    if javac "$JAVA_FILE" 2>/dev/null && \
       ENCRYPTED=$(java -cp "$TEMP_DIR" PasswordEncryptor "$AES_ENCRYPTION_KEY" "$PLAIN_PASSWORD" 2>/dev/null); then
        echo "✓ 加密成功！"
        echo ""
        echo "原始密码: $PLAIN_PASSWORD"
        echo "加密密码: $ENCRYPTED"
        echo ""
        echo "请将以下内容复制到 .env 文件中:"
        echo "DATABASE_PASSWORD=$ENCRYPTED"
        echo ""
        rm -rf "$TEMP_DIR"
        exit 0
    fi
    rm -rf "$TEMP_DIR"
fi

echo "错误: 无法加密密码"
echo "请确保系统已安装 Java"
exit 1
