package com.inkflow.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类
 * 
 * 使用AES-GCM-256加密算法，提供：
 * - 机密性（Confidentiality）
 * - 完整性（Integrity）
 * - 认证（Authentication）
 * 
 * 用于加密存储敏感信息，如API Key
 */
public final class CryptoUtil {
    
    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    
    /**
     * AES-GCM算法标识
     */
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    
    /**
     * GCM认证标签长度（128位）
     */
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * GCM初始化向量长度（12字节，推荐值）
     */
    private static final int GCM_IV_LENGTH = 12;
    
    /**
     * AES密钥长度（256位 = 32字节）
     */
    private static final int AES_KEY_LENGTH = 32;
    
    private static final SecureRandom secureRandom = new SecureRandom();
    
    private CryptoUtil() {
        // 工具类禁止实例化
    }
    
    /**
     * 使用AES-GCM-256加密数据
     * 
     * 输出格式: Base64(IV + 密文 + 认证标签)
     * 
     * @param plaintext 明文
     * @param key 加密密钥（Base64编码的32字节密钥）
     * @return Base64编码的密文
     * @throws CryptoException 加密失败时抛出
     */
    public static String encrypt(String plaintext, String key) {
        try {
            // 解码密钥
            SecretKey secretKey = decodeKey(key);
            
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // 加密
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // 组合 IV + 密文
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            log.error("加密失败", e);
            throw new CryptoException("加密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用AES-GCM-256解密数据
     * 
     * @param ciphertext Base64编码的密文（包含IV）
     * @param key 解密密钥（Base64编码的32字节密钥）
     * @return 解密后的明文
     * @throws CryptoException 解密失败时抛出
     */
    public static String decrypt(String ciphertext, String key) {
        try {
            // 解码密钥
            SecretKey secretKey = decodeKey(key);
            
            // 解码密文
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            
            // 提取IV和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encryptedData = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedData);
            
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // 解密
            byte[] plaintext = cipher.doFinal(encryptedData);
            
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("解密失败", e);
            throw new CryptoException("解密失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成随机的AES-256密钥
     * 
     * @return Base64编码的32字节密钥
     */
    public static String generateKey() {
        byte[] key = new byte[AES_KEY_LENGTH];
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
    
    /**
     * 验证密钥格式是否正确
     * 
     * @param key Base64编码的密钥
     * @return 是否有效
     */
    public static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(key);
            return decoded.length == AES_KEY_LENGTH;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 解码Base64密钥为SecretKey
     */
    private static SecretKey decodeKey(String key) {
        byte[] decodedKey = Base64.getDecoder().decode(key);
        if (decodedKey.length != AES_KEY_LENGTH) {
            throw new CryptoException("密钥长度必须为" + AES_KEY_LENGTH + "字节");
        }
        return new SecretKeySpec(decodedKey, "AES");
    }
    
    /**
     * 加密异常
     */
    public static class CryptoException extends RuntimeException {
        public CryptoException(String message) {
            super(message);
        }
        
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
