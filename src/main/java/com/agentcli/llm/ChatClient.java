package com.agentcli.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 客户端接口。
 *
 * 提供区块式与流式两种调用；区块式保留给后续 Planner / 上下文压缩等非交互场景。
 */
public interface ChatClient {

    /** 传入完整消息列表，返回模型回复文本。 */
    String call(List<Message> messages) throws Exception;

    /** 流式调用：每收到一段增量文本就回调 onDelta（在调用线程内同步回调）。 */
    void callStream(List<Message> messages, Consumer<String> onDelta) throws Exception;
}