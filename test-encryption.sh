#!/bin/bash
# 测试加密解密功能

set -e

echo "=========================================="
echo "测试加密解密功能"
echo "=========================================="
echo ""

# 从 .env 读取密钥
AES_KEY=$(grep "^AES_ENCRYPTION_KEY=" .env | cut -d'=' -f2-)

if [ -z "$AES_KEY" ]; then
    echo "错误: 未找到 AES_ENCRYPTION_KEY"
    exit 1
fi

echo "AES 密钥: ${AES_KEY:0:20}..."
echo "AES 密钥长度: ${#AES_KEY} 字符"
echo ""

# 测试密码
TEST_PASSWORD="password"
echo "测试密码: $TEST_PASSWORD"
echo ""

# 加密
echo "1. 加密测试..."
ENCRYPTED=$(./encrypt-password.sh "$TEST_PASSWORD" | grep "^加密密码:" | cut -d' ' -f2-)
echo "   加密结果: $ENCRYPTED"
echo ""

# 创建临时 Java 解密程序
TEMP_DIR=$(mktemp -d)
JAVA_FILE="$TEMP_DIR/PasswordDecryptor.java"

cat > "$JAVA_FILE" << 'EOFJ'
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PasswordDecryptor {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java PasswordDecryptor <key> <encrypted>");
            System.exit(1);
        }
        
        String key = args[0];
        String encrypted = args[1];
        
        try {
            // Base64 解码密钥
            byte[] keyBytes = Base64.getDecoder().decode(key);
            System.out.println("密钥字节长度: " + keyBytes.length);
            
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            String decryptedPassword = new String(decrypted, StandardCharsets.UTF_8);
            System.out.println("解密结果: " + decryptedPassword);
        } catch (Exception e) {
            System.err.println("解密失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOFJ

echo "2. 解密测试..."
javac "$JAVA_FILE"
java -cp "$TEMP_DIR" PasswordDecryptor "$AES_KEY" "$ENCRYPTED"
echo ""

rm -rf "$TEMP_DIR"

echo "=========================================="
echo "✓ 加密解密测试通过！"
echo "=========================================="
