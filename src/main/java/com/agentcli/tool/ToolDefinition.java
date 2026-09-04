package com.agentcli.tool;

/**
 * 工具定义：OpenAI 兼容的函数 schema。
 *
 * @param name           工具名
 * @param description    工具说明（供 LLM 判断何时调用）
 * @param parametersJson JSON Schema 字符串，例如
 *                       {"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}
 */
public record ToolDefinition(String name, String description, String parametersJson) {

    /** 序列化为 OpenAI tools 数组条目。 */
    public String toToolsJson() {
        return "{\"type\":\"function\",\"function\":{\"name\":\""
                + name + "\",\"description\":\"" + description
                + "\",\"parameters\":" + parametersJson + "}}";
    }
}