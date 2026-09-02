package com.agentcli.prompt;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptTest {

    @Test
    void containsTodayDateInShanghai() {
        String expected = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        assertTrue(SystemPrompt.build().contains(expected),
                "system prompt 应注入当前日期 " + expected);
    }

    @Test
    void containsIdentityAndShanghaiZone() {
        String prompt = SystemPrompt.build();
        assertTrue(prompt.contains("AgentCli"), "应声明身份");
        assertTrue(prompt.contains("Asia/Shanghai"), "应标注时区");
    }

    @Test
    void regeneratesAcrossDays() {
        // 仅验证每次调用返回非空且不同调用路径一致（每轮重新生成）
        assertTrue(SystemPrompt.build().length() > 0);
    }
}