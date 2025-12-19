package com.inkflow.module.auth.service;

import com.inkflow.common.util.CryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * API Key加密服务
 * 
 * 使用AES-GCM-256加密存储用户的AI服务API Key
 * 
 * 安全特性：
 * - AES-GCM-256加密（机密性+完整性+认证）
 * - 每次加密使用随机IV
 * - 密钥从环境变量加载
 */
@Service
public class ApiKeyService {
    
    private final String encryptionKey;
    
    public ApiKeyService(
        @Value("${inkflow.security.encryption-key}") String encryptionKey
    ) {
        this.encryptionKey = encryptionKey;
        
        // 验证密钥格式
        if (!CryptoUtil.isValidKey(encryptionKey)) {
            throw new IllegalArgumentException(
                "无效的加密密钥，请确保ENCRYPTION_KEY是Base64编码的32字节密钥"
            );
        }
    }
    
    /**
     * 加密API Key
     * 
     * @param apiKey 明文API Key
     * @return 加密后的API Key（Base64编码）
     */
    public String encryptApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return CryptoUtil.encrypt(apiKey, encryptionKey);
    }
    
    /**
     * 解密API Key
     * 
     * @param encryptedApiKey 加密的API Key
     * @return 明文API Key
     */
    public String decryptApiKey(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            return null;
        }
        return CryptoUtil.decrypt(encryptedApiKey, encryptionKey);
    }
    
    /**
     * 生成新的加密密钥（用于初始化配置）
     * 
     * @return Base64编码的32字节密钥
     */
    public static String generateNewEncryptionKey() {
        return CryptoUtil.generateKey();
    }
}
