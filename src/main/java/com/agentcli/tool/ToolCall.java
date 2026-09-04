package com.agentcli.tool;

/**
 * 一次模型发起的工具调用。
 *
 * @param id            工具调用 id（下一轮 role=tool 回传必须原样带上）
 * @param name          工具名
 * @param argumentsJson 参数（JSON 字符串，非对象，需二次解析）
 */
public record ToolCall(String id, String name, String argumentsJson) {
}