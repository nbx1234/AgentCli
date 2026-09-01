package com.agentcli.llm;

import java.util.List;

/**
 * LLM 客户端接口。
 *
 * 目前仅支持单次调用（一次传入全部历史消息），后续 Day 再演进为流式与工具调用。
 */
public interface ChatClient {

    /** 传入完整消息列表，返回模型回复文本。 */
    String call(List<Message> messages) throws Exception;
}