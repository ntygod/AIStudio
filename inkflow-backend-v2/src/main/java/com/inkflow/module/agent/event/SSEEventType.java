package com.inkflow.module.agent.event;

/**
 * SSE 事件类型
 * 用于流式响应的事件分类
 * 
 * Requirements: 15.3
 */
public enum SSEEventType {
    
    /**
     * 内容块 - AI 生成的文本内容
     */
    CONTENT("content"),
    
    /**
     * 思考过程 - Agent 的处理步骤
     */
    THOUGHT("thought"),
    
    /**
     * 工具开始 - Tool 调用开始
     */
    TOOL_START("tool_start"),
    
    /**
     * 工具结束 - Tool 调用完成
     */
    TOOL_END("tool_end"),
    
    /**
     * 数据块 - 结构化数据（如 JSON）
     */
    DATA_BLOCK("data_block"),
    
    /**
     * 警告 - 非致命问题
     */
    WARNING("warning"),
    
    /**
     * 错误 - 处理失败
     */
    ERROR("error"),
    
    /**
     * 完成 - 处理结束
     */
    DONE("done");
    
    private final String value;
    
    SSEEventType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
