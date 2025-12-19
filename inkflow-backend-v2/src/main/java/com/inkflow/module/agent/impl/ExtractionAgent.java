package com.inkflow.module.agent.impl;

import com.inkflow.module.agent.context.ContextBus;
import com.inkflow.module.agent.context.ContextEvent;
import com.inkflow.module.agent.core.*;
import com.inkflow.module.agent.dto.ChatRequest;
import com.inkflow.module.agent.lazy.LazyExecutionManager;
import com.inkflow.module.ai_bridge.chat.DynamicChatModelFactory;
import com.inkflow.module.project.entity.CreationPhase;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 实体抽取 Agent
 * 负责从文本中抽取角色、地点、物品等实体
 * 懒执行模式 - 按需触发
 * 
 * Requirements: 12.1-12.5
 */
@Slf4j
@Component
public class ExtractionAgent extends BaseAgent<ChatRequest, String> {

    private static final String AGENT_NAME = "ExtractionAgent";
    
    private final ContextBus contextBus;
    private final LazyExecutionManager lazyExecutionManager;

    public ExtractionAgent(
            DynamicChatModelFactory chatModelFactory,
            ApplicationEventPublisher eventPublisher,
            ContextBus contextBus,
            LazyExecutionManager lazyExecutionManager) {
        super(chatModelFactory, eventPublisher);
        this.contextBus = contextBus;
        this.lazyExecutionManager = lazyExecutionManager;
    }

    @PostConstruct
    public void init() {
        lazyExecutionManager.register(this);
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public AgentCapability getCapability() {
        return new AgentCapability(
            AGENT_NAME,
            AgentCategory.UTILITY,
            List.of(Intent.EXTRACT_ENTITY),
            List.of(CreationPhase.WRITING, CreationPhase.REVISION),
            List.of(),
            ExecutionMode.LAZY,  // 懒执行模式
            2000,   // 预估延迟 2s
            1000    // 预估 Token 消耗
        );
    }

    @Override
    protected String getSystemPrompt() {
        return """
            你是一位专业的文本分析专家，擅长从小说文本中抽取结构化信息。
            
            你的职责：
            1. 抽取角色实体（姓名、称号、身份）
            2. 抽取地点实体（地名、场所）
            3. 抽取物品实体（道具、武器、宝物）
            4. 抽取关系（角色间关系、从属关系）
            5. 抽取事件（重要情节点）
            
            抽取原则：
            - 准确识别实体边界
            - 区分实体类型
            - 识别实体间关系
            - 标注首次出现位置
            
            输出格式（JSON）：
            {
              "characters": [
                {
                  "name": "角色名",
                  "aliases": ["别名1", "别名2"],
                  "role": "身份/职业",
                  "firstMention": "首次出现的句子"
                }
              ],
              "locations": [
                {
                  "name": "地点名",
                  "type": "类型（城市/山脉/建筑等）",
                  "description": "简短描述"
                }
              ],
              "items": [
                {
                  "name": "物品名",
                  "type": "类型（武器/道具/宝物等）",
                  "owner": "所有者（如有）"
                }
              ],
              "relationships": [
                {
                  "from": "实体1",
                  "to": "实体2",
                  "type": "关系类型",
                  "description": "关系描述"
                }
              ],
              "events": [
                {
                  "description": "事件描述",
                  "participants": ["参与者1", "参与者2"],
                  "location": "发生地点"
                }
              ]
            }
            
            请用中文回复，输出有效的 JSON 格式。
            """;
    }

    @Override
    protected String buildUserPrompt(ChatRequest input) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请从以下文本中抽取实体和关系：\n\n");
        prompt.append("【待分析文本】\n").append(input.message());
        
        return prompt.toString();
    }

    @Override
    protected String parseResponse(String response, ChatRequest input) {
        // 记录抽取完成事件
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_COMPLETED.name(),
                "实体抽取完成",
                Map.of(
                    "projectId", input.projectId().toString(),
                    "triggerRagUpdate", true  // 标记需要触发 RAG 索引更新
                )
            ));
        }
        return response;
    }

    /**
     * 流式实体抽取
     */
    @Override
    public Flux<String> stream(ChatRequest input) {
        publishThought("开始抽取实体...");
        
        if (input.sessionId() != null) {
            contextBus.publish(input.sessionId(), ContextEvent.custom(
                AGENT_NAME,
                ContextEvent.EventType.AGENT_STARTED.name(),
                "开始实体抽取",
                Map.of("projectId", input.projectId().toString())
            ));
        }

        try {
            ChatModel model = chatModelFactory.getDefaultModel();
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(getSystemPrompt())
                    .build();

            String userPrompt = buildUserPrompt(input);

            return client.prompt()
                    .user(userPrompt)
                    .stream()
                    .content()
                    .doOnComplete(() -> {
                        publishThought("实体抽取完成");
                        if (input.sessionId() != null) {
                            contextBus.publish(input.sessionId(), ContextEvent.custom(
                                AGENT_NAME,
                                ContextEvent.EventType.AGENT_COMPLETED.name(),
                                "实体抽取完成",
                                Map.of("triggerRagUpdate", true)
                            ));
                        }
                    })
                    .doOnError(e -> {
                        publishThought("抽取失败: " + e.getMessage());
                        log.error("[ExtractionAgent] 流式生成失败", e);
                    });
        } catch (Exception e) {
            log.error("[ExtractionAgent] 创建 ChatClient 失败", e);
            return Flux.error(e);
        }
    }
}
