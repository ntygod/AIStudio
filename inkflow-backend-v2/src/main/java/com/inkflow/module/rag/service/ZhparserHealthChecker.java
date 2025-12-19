package com.inkflow.module.rag.service;

import com.inkflow.module.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * zhparser 中文分词扩展健康检查组件
 * 
 * 在应用启动时验证 zhparser 扩展的可用性，并提供运行时查询方法。
 * 如果 zhparser 不可用，系统将优雅降级到 simple 配置。
 * 
 * 功能：
 * - 启动时检查 zhparser 扩展是否安装
 * - 验证 chinese 全文搜索配置是否正常工作
 * - 提供 isZhparserAvailable() 方法供其他服务查询
 * - 提供 getEffectiveLanguage() 方法获取实际使用的语言配置
 * 
 * @author zsg
 * @date 2025/12/18
 * @see RagProperties.FullTextConfig
 * 
 * Requirements: 6.1, 1.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZhparserHealthChecker implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;

    /**
     * zhparser 扩展可用性状态
     * 使用 volatile 确保多线程可见性
     */
    private volatile boolean zhparserAvailable = false;

    /**
     * chinese 配置可用性状态
     */
    private volatile boolean chineseConfigAvailable = false;

    /**
     * 应用启动时执行健康检查
     * 
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        checkZhparserAvailability();
    }

    /**
     * 检查 zhparser 扩展可用性
     * 
     * 检查步骤：
     * 1. 检查 zhparser 扩展是否已安装
     * 2. 验证 chinese 全文搜索配置是否可用
     * 3. 测试分词功能是否正常工作
     * 
     * 如果任何步骤失败，将记录警告并设置降级标志
     */
    public void checkZhparserAvailability() {
        try {
            // Step 1: 检查 zhparser 扩展是否安装
            boolean extensionExists = checkExtensionExists();
            
            if (!extensionExists) {
                log.warn("zhparser extension is not installed, falling back to simple configuration");
                zhparserAvailable = false;
                chineseConfigAvailable = false;
                return;
            }
            
            log.debug("zhparser extension found in database");
            
            // Step 2: 检查 chinese 配置是否存在
            boolean configExists = checkChineseConfigExists();
            
            if (!configExists) {
                log.warn("chinese text search configuration not found, falling back to simple configuration");
                zhparserAvailable = true;
                chineseConfigAvailable = false;
                return;
            }
            
            log.debug("chinese text search configuration found");
            
            // Step 3: 验证分词功能是否正常工作
            boolean segmentationWorks = testSegmentation();
            
            if (!segmentationWorks) {
                log.warn("zhparser segmentation test failed, falling back to simple configuration");
                zhparserAvailable = true;
                chineseConfigAvailable = false;
                return;
            }
            
            // 所有检查通过
            zhparserAvailable = true;
            chineseConfigAvailable = true;
            log.info("zhparser extension is available and configured correctly for Chinese full-text search");
            
        } catch (Exception e) {
            log.warn("zhparser availability check failed: {}, falling back to simple configuration", 
                    e.getMessage());
            zhparserAvailable = false;
            chineseConfigAvailable = false;
        }
    }

    /**
     * 检查 zhparser 扩展是否已安装
     * 
     * @return true 如果扩展已安装
     */
    private boolean checkExtensionExists() {
        try {
            String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM pg_extension WHERE extname = 'zhparser'
                )
                """;
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check zhparser extension: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 chinese 全文搜索配置是否存在
     * 
     * @return true 如果配置存在
     */
    private boolean checkChineseConfigExists() {
        try {
            String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM pg_ts_config WHERE cfgname = 'chinese'
                )
                """;
            Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.debug("Failed to check chinese configuration: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试 zhparser 分词功能是否正常工作
     * 
     * @return true 如果分词功能正常
     */
    private boolean testSegmentation() {
        try {
            // 使用简单的中文文本测试分词
            String sql = "SELECT to_tsvector('chinese', '测试中文分词')";
            String result = jdbcTemplate.queryForObject(sql, String.class);
            
            // 验证结果不为空且包含分词结果
            boolean success = result != null && !result.isEmpty() && !result.equals("''");
            
            if (success) {
                log.debug("zhparser segmentation test passed, result: {}", result);
            }
            
            return success;
        } catch (Exception e) {
            log.debug("zhparser segmentation test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 zhparser 扩展是否可用
     * 
     * @return true 如果 zhparser 扩展已安装且可用
     */
    public boolean isZhparserAvailable() {
        return zhparserAvailable;
    }

    /**
     * 检查 chinese 全文搜索配置是否可用
     * 
     * @return true 如果 chinese 配置可用
     */
    public boolean isChineseConfigAvailable() {
        return chineseConfigAvailable;
    }

    /**
     * 获取有效的搜索语言配置
     * 
     * 根据配置和 zhparser 可用性返回实际使用的语言：
     * - 如果配置为 "chinese" 且 zhparser 可用，返回 "chinese"
     * - 如果配置为 "chinese" 但 zhparser 不可用，降级返回 "simple"
     * - 其他配置直接返回配置值
     * 
     * @return 有效的语言配置字符串
     */
    public String getEffectiveLanguage() {
        String configuredLanguage = ragProperties.fullText().language();
        
        if ("chinese".equalsIgnoreCase(configuredLanguage)) {
            if (zhparserAvailable && chineseConfigAvailable) {
                return "chinese";
            }
            log.debug("Configured language 'chinese' unavailable, degrading to 'simple'");
            return "simple";
        }
        
        // 对于其他语言配置，直接返回
        if (configuredLanguage != null && !configuredLanguage.isBlank()) {
            return configuredLanguage.toLowerCase();
        }
        
        return "simple";
    }

    /**
     * 获取 zhparser 健康状态摘要
     * 
     * @return 健康状态描述字符串
     */
    public String getHealthStatus() {
        if (zhparserAvailable && chineseConfigAvailable) {
            return "zhparser: available, chinese config: available, effective language: " 
                    + getEffectiveLanguage();
        } else if (zhparserAvailable) {
            return "zhparser: available, chinese config: unavailable, effective language: " 
                    + getEffectiveLanguage();
        } else {
            return "zhparser: unavailable, effective language: " + getEffectiveLanguage();
        }
    }

    /**
     * 手动触发重新检查 zhparser 可用性
     * 可用于运行时配置变更后的重新验证
     */
    public void recheckAvailability() {
        log.info("Rechecking zhparser availability...");
        checkZhparserAvailability();
    }
}
