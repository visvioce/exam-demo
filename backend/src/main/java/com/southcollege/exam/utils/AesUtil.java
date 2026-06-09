package com.southcollege.exam.utils;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AES 加密工具类
 * 用于加密敏感数据（如 API Key）
 * <p>
 * 注意：每个方法内部创建 AES 实例，确保线程安全
 * </p>
 */
@Component
public class AesUtil {

    @Value("${aes.secret:}")
    private String secretKey;

    private static volatile String staticSecretKey;

    @PostConstruct
    public void init() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "AES 密钥未配置！请在 .env 文件中设置 AES_SECRET=xxx 或通过环境变量设置。\n" +
                "生成密钥示例: openssl rand -base64 32"
            );
        }
        staticSecretKey = secretKey;
    }

    /**
     * 创建 AES 实例（线程安全，每次调用创建新实例）
     */
    private static AES createAes() {
        String keyStr = staticSecretKey;
        if (keyStr == null) {
            throw new IllegalStateException("AES 密钥未初始化，请检查 aes.secret 配置是否正确加载");
        }
        byte[] key = padKey(keyStr.getBytes(StandardCharsets.UTF_8));
        return SecureUtil.aes(key);
    }

    /**
     * 加密
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        // 每次加密创建新的 AES 实例，确保线程安全
        AES aes = createAes();
        byte[] encrypted = aes.encrypt(plainText);
        return HexUtil.encodeHexStr(encrypted);
    }

    /**
     * 解密
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        // 每次解密创建新的 AES 实例，确保线程安全
        AES aes = createAes();
        byte[] decrypted = aes.decrypt(HexUtil.decodeHex(encryptedText));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 填充 key 到 16 字节
     */
    private static byte[] padKey(byte[] key) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(key);
            byte[] result = new byte[16];
            System.arraycopy(hash, 0, result, 0, 16);
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
