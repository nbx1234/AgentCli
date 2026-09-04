package com.agentcli.llm;

import com.agentcli.tool.ToolDefinition;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 客户端接口。
 *
 * 提供区块式与流式两种调用；区块式保留给后续 Planner / 上下文压缩等非交互场景，
 * 并用带 tools 的重载为 ReAct 提供工具调用解析。
 */
public interface ChatClient {

    /** 传入完整消息列表（不带工具），返回模型回复文本。 */
    String call(List<Message> messages) throws Exception;

    /** 带工具定义的区块式调用：返回文本 + 解析出的工具调用。 */
    LlmResponse call(List<Message> messages, List<ToolDefinition> tools) throws Exception;

    /** 流式调用：每收到一段增量文本就回调 onDelta（在调用线程内同步回调）。 */
    void callStream(List<Message> messages, Consumer<String> onDelta) throws Exception;
}