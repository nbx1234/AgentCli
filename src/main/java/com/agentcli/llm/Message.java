package com.agentcli.llm;

import com.agentcli.tool.ToolCall;

import java.util.List;

/**
 * 一条对话消息。
 *
 * 从纯 content record 演进为可携带工具字段的 class：
 * - role：system / user / assistant / tool
 * - content：普通文本（assistant 携带工具调用时可为空串）
 * - toolCalls：assistant 消息携带的工具调用列表（可选）
 * - toolCallId：role=tool 消息用于回传对应调用 id（可选）
 */
public class Message {

    private final String role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;

    public Message(String role, String content) {
        this(role, content, null, null);
    }

    public Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    /** assistant 消息携带若干工具调用。 */
    public static Message assistantWithTools(List<ToolCall> toolCalls) {
        return new Message("assistant", "", toolCalls, null);
    }

    /** role=tool 消息：回传某次调用的结果。 */
    public static Message toolResult(String toolCallId, String result) {
        return new Message("tool", result, null, toolCallId);
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public String toolCallId() {
        return toolCallId;
    }
}