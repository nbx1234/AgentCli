package com.agentcli.web;

import java.util.Map;

/**
 * 事件广播接口：把 Agent 的 ReAct 过程事件发给不同去处。
 *
 * 实现有：终端（ConsoleSink）与浏览器 SSE（WebSink），可多个挂一起。
 */
@FunctionalInterface
public interface EventEmitter {

    EventEmitter NOOP = (type, payload) -> { };

    /**
     * 发出一个事件。
     *
     * @param type    事件类型，如 turn_start / llm_call / tool_call / tool_result / turn_end
     * @param payload 事件附加数据
     */
    void emit(String type, Map<String, Object> payload);
}