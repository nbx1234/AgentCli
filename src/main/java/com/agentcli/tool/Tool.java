package com.agentcli.tool;

import java.util.Map;

/**
 * 可执行工具：描述自身的 schema，并提供执行逻辑。
 *
 * 参数解析失败/执行异常统一由 ToolRegistry 转成字符串结果，调用方（Agent/LLM）自行处理。
 */
public interface Tool {

    /** 工具的 OpenAI 兼容 schema。 */
    ToolDefinition definition();

    /** 执行工具，返回字符串结果。 */
    String execute(Map<String, Object> args);
}