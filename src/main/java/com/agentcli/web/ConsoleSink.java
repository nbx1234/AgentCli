package com.agentcli.web;

import java.util.Map;

/**
 * 终端事件输出：把 Agent 过程事件印到控制台。
 *
 * 只保留最有用的 tool_call 行（与 Day 6 的 `⚡ tool: ...` 一致，保证零回归）；
 * 其余事件静默，避免刷屏。
 */
public class ConsoleSink implements EventEmitter {

    @Override
    public void emit(String type, Map<String, Object> payload) {
        if ("tool_call".equals(type)) {
            System.out.println("⚡ tool: " + payload.get("tool")
                    + "(" + abbreviate(String.valueOf(payload.get("args"))) + ")");
        }
    }

    private static String abbreviate(String s) {
        if (s == null || s.length() <= 100) {
            return s;
        }
        return s.substring(0, 100) + "…";
    }
}