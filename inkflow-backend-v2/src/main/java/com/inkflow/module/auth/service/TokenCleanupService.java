package com.inkflow.module.auth.service;

import com.inkflow.module.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 令牌清理服务
 * 
 * 定时清理过期和已撤销的刷新令牌，保持数据库整洁
 */
@Service
public class TokenCleanupService {
    
    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);
    
    private final RefreshTokenRepository refreshTokenRepository;
    
    public TokenCleanupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }
    
    /**
     * 每天凌晨3点清理过期令牌
     * 
     * 清理规则：
     * - 已过期的令牌
     * - 已撤销超过7天的令牌
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        // 已撤销超过7天的令牌可以删除
        LocalDateTime cutoff = now.minusDays(7);
        
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(now, cutoff);
        
        if (deletedCount > 0) {
            log.info("清理过期令牌完成，删除数量: {}", deletedCount);
        }
    }
}
