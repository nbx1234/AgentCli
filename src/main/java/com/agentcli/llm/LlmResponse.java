package com.agentcli.llm;

import com.agentcli.tool.ToolCall;

import java.util.List;

/**
 * 模型返回结果：普通文本内容 + （可选）工具调用列表。
 *
 * @param content    回复文本（无文本时可能为空串）
 * @param toolCalls  模型发起的工具调用；无则为空列表
 */
public record LlmResponse(String content, List<ToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}