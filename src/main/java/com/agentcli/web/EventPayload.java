package com.agentcli.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件 payload 的标准化构造器。
 *
 * 统一 schema：默认带 {@code type} 与 {@code ts}（毫秒时间戳），可选字段通过 {@link #put} 追加，
 * 如 iteration / tool / args / result / durationMs / preview / answer / text。
 */
public final class EventPayload {

    private final Map<String, Object> m;

    private EventPayload(String type) {
        m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("ts", System.currentTimeMillis());
    }

    public static EventPayload create(String type) {
        return new EventPayload(type);
    }

    public EventPayload put(String key, Object value) {
        m.put(key, value);
        return this;
    }

    public EventPayload iteration(int i) {
        return put("iteration", i);
    }

    public EventPayload durationMs(long d) {
        return put("durationMs", d);
    }

    public Map<String, Object> build() {
        return m;
    }
}