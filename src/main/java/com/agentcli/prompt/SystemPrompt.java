package com.agentcli.prompt;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * System Prompt 组装：每轮调用都重新生成（日期可能跨天），性能开销极低。
 */
public final class SystemPrompt {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private SystemPrompt() {
    }

    /** 返回当前 system prompt，含身份、当前日期/时区、简洁规则。 */
    public static String build() {
        LocalDate today = LocalDate.now(ZONE);
        return "你是 AgentCli，一个面向 vibe coding 的 Java 命令行智能体。"
                + "当前日期：" + today + "（时区 " + ZONE.getId() + "）。"
                + "若用户提到相对时间（如今天/昨天），据此推算。"
                + "回答简洁、直接，不要客套，不做无谓重复。";
    }
}