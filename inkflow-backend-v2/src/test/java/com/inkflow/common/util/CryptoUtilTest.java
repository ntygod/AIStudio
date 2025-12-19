package com.inkflow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 加密工具类单元测试
 */
@DisplayName("加密工具类测试")
class CryptoUtilTest {
    
    @Test
    @DisplayName("生成密钥应返回有效的Base64编码32字节密钥")
    void generateKey_shouldReturnValidKey() {
        // When
        String key = CryptoUtil.generateKey();
        
        // Then
        assertThat(key).isNotBlank();
        assertThat(CryptoUtil.isValidKey(key)).isTrue();
    }
    
    @Test
    @DisplayName("加密解密往返应保持数据一致")
    void encryptDecrypt_shouldMaintainDataIntegrity() {
        // Given
        String key = CryptoUtil.generateKey();
        String plaintext = "sk-test-api-key-12345";
        
        // When
        String encrypted = CryptoUtil.encrypt(plaintext, key);
        String decrypted = CryptoUtil.decrypt(encrypted, key);
        
        // Then
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
    }
    
    @Test
    @DisplayName("相同明文加密两次应产生不同密文（随机IV）")
    void encrypt_shouldProduceDifferentCiphertextForSamePlaintext() {
        // Given
        String key = CryptoUtil.generateKey();
        String plaintext = "sk-test-api-key-12345";
        
        // When
        String encrypted1 = CryptoUtil.encrypt(plaintext, key);
        String encrypted2 = CryptoUtil.encrypt(plaintext, key);
        
        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        
        // 但解密后应该相同
        assertThat(CryptoUtil.decrypt(encrypted1, key)).isEqualTo(plaintext);
        assertThat(CryptoUtil.decrypt(encrypted2, key)).isEqualTo(plaintext);
    }
    
    @Test
    @DisplayName("使用错误密钥解密应抛出异常")
    void decrypt_shouldThrowExceptionWithWrongKey() {
        // Given
        String key1 = CryptoUtil.generateKey();
        String key2 = CryptoUtil.generateKey();
        String plaintext = "sk-test-api-key-12345";
        String encrypted = CryptoUtil.encrypt(plaintext, key1);
        
        // When & Then
        assertThatThrownBy(() -> CryptoUtil.decrypt(encrypted, key2))
            .isInstanceOf(CryptoUtil.CryptoException.class);
    }
    
    @Test
    @DisplayName("验证无效密钥应返回false")
    void isValidKey_shouldReturnFalseForInvalidKey() {
        // When & Then
        assertThat(CryptoUtil.isValidKey(null)).isFalse();
        assertThat(CryptoUtil.isValidKey("")).isFalse();
        assertThat(CryptoUtil.isValidKey("short")).isFalse();
        assertThat(CryptoUtil.isValidKey("not-base64!@#$")).isFalse();
    }
    
    @Test
    @DisplayName("加密中文内容应正常工作")
    void encrypt_shouldWorkWithChineseContent() {
        // Given
        String key = CryptoUtil.generateKey();
        String plaintext = "这是一个中文API密钥测试";
        
        // When
        String encrypted = CryptoUtil.encrypt(plaintext, key);
        String decrypted = CryptoUtil.decrypt(encrypted, key);
        
        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }
    
    @Test
    @DisplayName("加密长文本应正常工作")
    void encrypt_shouldWorkWithLongText() {
        // Given
        String key = CryptoUtil.generateKey();
        String plaintext = "a".repeat(10000);
        
        // When
        String encrypted = CryptoUtil.encrypt(plaintext, key);
        String decrypted = CryptoUtil.decrypt(encrypted, key);
        
        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }
}
