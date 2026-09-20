package com.agentcli.context;

import com.agentcli.llm.Message;

import java.util.List;

/**
 * Day 19：Token 估算器。
 *
 * 不为每个 token 精确计数（那需要分词器），用启发式<b>宁大勿小</b>：
 * CJK 字符 ≈ 1 token，ASCII 字符 ≈ 1/4 token（约 4 字符 1 token），整体上浮 10%。
 * 误触发压缩只是丢点细节，估小了则是 API 直接报 context length exceeded。
 */
public final class TokenEstimator {

    private static final double BUFFER = 1.10; // 保守上浮 10%

    /** 估一串文本的 token 数。 */
    public int estimate(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        double t = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int adv = Character.charCount(cp);
            t += isCjk(cp) ? 1.0 : 0.25;
            i += adv;
        }
        return (int) Math.ceil(t * BUFFER);
    }

    /** 估整个历史（含工具调用的 token 数）。 */
    public int estimate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message m : messages) {
            total += estimate(m.content());
            if (m.toolCalls() != null) {
                for (var tc : m.toolCalls()) {
                    total += estimate(tc.name());
                    total += estimate(tc.argumentsJson());
                }
            }
        }
        return total;
    }

    /** 是否 CJK 字符：中文/CJK 统一表意文字、假名、谚文。 */
    static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0xAC00 && cp <= 0xD7A3);
    }
}