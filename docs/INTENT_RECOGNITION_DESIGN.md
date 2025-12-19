# 意图识别设计文档

## 概述

意图识别是对话编排系统的核心组件，负责理解用户输入的真实意图，以便系统做出正确的响应。

## 意图类型定义

```java
public enum UserIntent {
    INITIALIZE_PROJECT,    // 创建新项目："我想创作一个修仙小说"
    PROVIDE_KEYWORDS,      // 提供关键词："热血少年，现代修仙"
    GENERATE_OUTLINE,      // 生成大纲："帮我生成故事大纲"
    GENERATE_CHAPTER,      // 生成章节："生成第一章"
    GENERATE_BLOCK,        // 生成剧情块："生成这个场景的内容"
    EDIT_ENTITY,          // 编辑实体："修改主角的性格"
    ASK_QUESTION,         // 提问："修仙小说应该怎么写？"
    CONTINUE_GENERATION,  // 继续生成："继续"
    REGENERATE,           // 重新生成："重新生成"
    GENERAL_CHAT          // 通用对话："今天天气真好"
}
```

## 实现方案：混合识别

### 架构图

```
用户输入
    ↓
┌─────────────────────────────────────┐
│  IntentRecognitionService           │
├─────────────────────────────────────┤
│  1. 规则引擎（快速判断）             │
│     ├─ 关键词匹配                    │
│     ├─ 正则表达式                    │
│     └─ 上下文规则                    │
│                                     │
│  2. AI 识别（复杂情况）              │
│     ├─ Few-Shot Prompt              │
│     ├─ 上下文注入                    │
│     └─ 结果解析                      │
└─────────────────────────────────────┘
    ↓
识别结果 + 置信度
```

### 核心代码

```java
@Service
@RequiredArgsConstructor
public class IntentRecognitionService {
    
    private final AIService aiService;
    
    /**
     * 识别用户意图
     * 
     * @param userInput 用户输入
     * @param context 创作上下文
     * @return 识别结果
     */
    public IntentResult recognizeIntent(String userInput, CreationContext context) {
        // 1. 先用规则快速判断
        IntentResult ruleResult = recognizeByRules(userInput, context);
        
        if (ruleResult.getConfidence() >= 0.9) {
            log.debug("规则识别成功: intent={}, confidence={}", 
                ruleResult.getIntent(), ruleResult.getConfidence());
            return ruleResult;
        }
        
        // 2. 规则无法确定，使用 AI 识别
        log.debug("规则识别置信度低，使用 AI 识别");
        return recognizeByAI(userInput, context);
    }
    
    /**
     * 基于规则的意图识别
     */
    private IntentResult recognizeByRules(String userInput, CreationContext context) {
        String input = userInput.toLowerCase().trim();
        
        // 规则1：项目初始化
        if (context.getProjectId() == null) {
            if (matchesPattern(input, "^(我想|想要|帮我|开始).*(创作|写|新建).*小说")) {
                return IntentResult.of(UserIntent.INITIALIZE_PROJECT, 0.95);
            }
        }
        
        // 规则2：提供关键词（在初始化阶段）
        if (context.getPhase() == CreationPhase.INITIALIZATION) {
            if (containsKeywords(input, "修仙", "玄幻", "都市", "科幻") &&
                !input.contains("?") && !input.contains("？")) {
                return IntentResult.of(UserIntent.PROVIDE_KEYWORDS, 0.9);
            }
        }
        
        // 规则3：生成大纲
        if (matchesPattern(input, ".*(大纲|结构|章节安排|故事框架).*")) {
            return IntentResult.of(UserIntent.GENERATE_OUTLINE, 0.9);
        }
        
        // 规则4：生成章节
        if (matchesPattern(input, ".*(生成|写|创作).*(第.{1,3}章|章节|正文).*")) {
            return IntentResult.of(UserIntent.GENERATE_CHAPTER, 0.9);
        }
        
        // 规则5：编辑实体
        if (matchesPattern(input, ".*(修改|编辑|更新|调整).*(主角|角色|人物|世界观|设定).*")) {
            return IntentResult.of(UserIntent.EDIT_ENTITY, 0.85);
        }
        
        // 规则6：提问
        if (input.endsWith("?") || input.endsWith("？") ||
            input.startsWith("怎么") || input.startsWith("如何") ||
            input.startsWith("为什么") || input.startsWith("什么是")) {
            return IntentResult.of(UserIntent.ASK_QUESTION, 0.9);
        }
        
        // 规则7：继续生成
        if (input.equals("继续") || input.equals("continue")) {
            return IntentResult.of(UserIntent.CONTINUE_GENERATION, 1.0);
        }
        
        // 规则8：重新生成
        if (matchesPattern(input, "^(重新|再).*(生成|写).*")) {
            return IntentResult.of(UserIntent.REGENERATE, 0.95);
        }
        
        // 无法确定，返回低置信度结果
        return IntentResult.of(UserIntent.GENERAL_CHAT, 0.3);
    }
    
    /**
     * 基于 AI 的意图识别
     */
    private IntentResult recognizeByAI(String userInput, CreationContext context) {
        String prompt = buildIntentPrompt(userInput, context);
        
        GenerateContentRequest request = GenerateContentRequest.builder()
            .provider("openai")
            .messages(List.of(
                ChatMessage.system("你是一个意图识别专家。"),
                ChatMessage.user(prompt)
            ))
            .config(AIConfig.builder()
                .maxTokens(50)
                .temperature(0.1)
                .build())
            .build();
        
        try {
            String response = aiService.generateContent(context.getUserId(), request)
                .block();
            
            UserIntent intent = parseIntent(response);
            return IntentResult.of(intent, 0.85);
            
        } catch (Exception e) {
            log.error("AI 意图识别失败: {}", e.getMessage());
            // 降级：返回通用对话
            return IntentResult.of(UserIntent.GENERAL_CHAT, 0.5);
        }
    }
    
    /**
     * 构建意图识别 Prompt
     */
    private String buildIntentPrompt(String userInput, CreationContext context) {
        return String.format("""
            请分析用户输入，返回对应的意图类型。
            
            意图类型：
            - INITIALIZE_PROJECT: 创建新项目
            - PROVIDE_KEYWORDS: 提供关键词
            - GENERATE_OUTLINE: 生成故事大纲
            - GENERATE_CHAPTER: 生成章节内容
            - GENERATE_BLOCK: 生成剧情块
            - EDIT_ENTITY: 编辑实体
            - ASK_QUESTION: 提问
            - CONTINUE_GENERATION: 继续生成
            - REGENERATE: 重新生成
            - GENERAL_CHAT: 闲聊
            
            示例：
            输入："我想写一个科幻小说" → INITIALIZE_PROJECT
            输入："热血少年，现代修仙" → PROVIDE_KEYWORDS
            输入："帮我生成第一章" → GENERATE_CHAPTER
            输入："修改主角的性格" → EDIT_ENTITY
            输入："继续" → CONTINUE_GENERATION
            
            当前上下文：
            - 项目: %s
            - 阶段: %s
            - 最近操作: %s
            
            用户输入："%s"
            
            只返回意图类型（大写），不要其他内容。
            """,
            context.getProjectId() == null ? "无" : "已创建",
            context.getPhase(),
            context.getLastAction(),
            userInput
        );
    }
    
    /**
     * 解析 AI 返回的意图
     */
    private UserIntent parseIntent(String response) {
        String cleaned = response.trim().toUpperCase()
            .replaceAll("[^A-Z_]", "");
        
        try {
            return UserIntent.valueOf(cleaned);
        } catch (IllegalArgumentException e) {
            log.warn("无法解析意图: {}", response);
            return UserIntent.GENERAL_CHAT;
        }
    }
    
    // 辅助方法
    private boolean matchesPattern(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }
    
    private boolean containsKeywords(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

/**
 * 意图识别结果
 */
@Data
@AllArgsConstructor(staticName = "of")
public class IntentResult {
    private UserIntent intent;
    private double confidence;  // 0.0 - 1.0
}

/**
 * 创作上下文
 */
@Data
@Builder
public class CreationContext {
    private UUID userId;
    private UUID projectId;
    private CreationPhase phase;
    private String lastAction;  // 最近一次操作
}
```

## 优化策略

### 1. 缓存常见意图

```java
@Service
public class IntentCacheService {
    
    private final Cache<String, IntentResult> cache = 
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    
    public IntentResult getOrRecognize(String userInput, CreationContext context,
                                       Supplier<IntentResult> recognizer) {
        String cacheKey = buildCacheKey(userInput, context);
        return cache.get(cacheKey, k -> recognizer.get());
    }
}
```

### 2. 记录识别日志

```java
@Service
public class IntentLoggingService {
    
    public void logRecognition(String userInput, IntentResult result, 
                               CreationContext context) {
        IntentLog log = IntentLog.builder()
            .userInput(userInput)
            .recognizedIntent(result.getIntent())
            .confidence(result.getConfidence())
            .context(context)
            .timestamp(Instant.now())
            .build();
        
        // 保存到数据库，用于后续分析和优化
        intentLogRepository.save(log);
    }
}
```

### 3. A/B 测试

```java
@Service
public class IntentABTestService {
    
    public IntentResult recognizeWithABTest(String userInput, CreationContext context) {
        // 50% 用户使用规则，50% 使用 AI
        if (Math.random() < 0.5) {
            return ruleBasedService.recognize(userInput, context);
        } else {
            return aiBasedService.recognize(userInput, context);
        }
    }
}
```

## 性能指标

### 目标

- **准确率**: > 90%
- **响应时间**: 
  - 规则识别: < 10ms
  - AI 识别: < 500ms
- **可用性**: > 99.9%

### 监控

```java
@Aspect
@Component
public class IntentRecognitionMetrics {
    
    private final MeterRegistry registry;
    
    @Around("execution(* IntentRecognitionService.recognizeIntent(..))")
    public Object measureRecognition(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start(registry);
        
        try {
            Object result = joinPoint.proceed();
            
            // 记录成功指标
            sample.stop(Timer.builder("intent.recognition.duration")
                .tag("status", "success")
                .register(registry));
            
            return result;
            
        } catch (Exception e) {
            // 记录失败指标
            sample.stop(Timer.builder("intent.recognition.duration")
                .tag("status", "error")
                .register(registry));
            throw e;
        }
    }
}
```

## 测试策略

### 单元测试

```java
@Test
public void testInitializeProjectIntent() {
    CreationContext context = CreationContext.builder()
        .projectId(null)  // 无项目
        .phase(CreationPhase.WELCOME)
        .build();
    
    IntentResult result = service.recognizeIntent("我想创作一个修仙小说", context);
    
    assertEquals(UserIntent.INITIALIZE_PROJECT, result.getIntent());
    assertTrue(result.getConfidence() > 0.9);
}
```

### 集成测试

```java
@Test
public void testFullConversationFlow() {
    // 测试完整对话流程的意图识别
    List<String> inputs = List.of(
        "我想创作一个修仙小说",
        "热血少年，现代修仙",
        "帮我生成大纲",
        "生成第一章"
    );
    
    List<UserIntent> expected = List.of(
        UserIntent.INITIALIZE_PROJECT,
        UserIntent.PROVIDE_KEYWORDS,
        UserIntent.GENERATE_OUTLINE,
        UserIntent.GENERATE_CHAPTER
    );
    
    // 验证每一步的意图识别
    for (int i = 0; i < inputs.size(); i++) {
        IntentResult result = service.recognizeIntent(inputs.get(i), context);
        assertEquals(expected.get(i), result.getIntent());
    }
}
```

## 总结

意图识别采用**混合方案**：
1. **规则引擎**处理 80% 的常见场景（快速、可控）
2. **AI 识别**处理 20% 的复杂场景（智能、灵活）
3. **缓存机制**提高性能
4. **日志记录**用于持续优化
5. **A/B 测试**验证效果

这种方案在准确率、性能和成本之间取得了良好的平衡。
