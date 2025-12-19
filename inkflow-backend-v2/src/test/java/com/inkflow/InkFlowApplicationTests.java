package com.inkflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用启动测试
 */
@SpringBootTest
@ActiveProfiles("test")
class InkFlowApplicationTests {

    @Test
    void contextLoads() {
        // 验证Spring上下文能够正常加载
    }
}
