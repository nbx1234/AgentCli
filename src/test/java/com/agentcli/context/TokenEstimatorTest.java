package com.agentcli.context;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day 19：Token 估算与上下文压缩测试。
 * 覆盖估算器边界（纯中文/纯英文/混合/空）与压缩保序断言。
 */
class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    // ---- 估算器边界 ----

    @Test
    void emptyTextIsZero() {
        assertEquals(0, estimator.estimate(""));
        assertEquals(0, estimator.estimate((String) null));
        assertEquals(0, estimator.estimate(List.of()));
    }

    @Test
    void cjkCharApproxOneToken() {
        // 4 个中文字，每个 ~1 token
        assertEquals(5, estimator.estimate("你好世界")); // ceil(4 × 1.10)
    }

    @Test
    void asciiCharApproxQuarterToken() {
        // 4 个 ASCII 字符 ≈ 1 token，×1.10 → ceil(1.10) = 2
        assertEquals(2, estimator.estimate("abcd"));
    }

    @Test
    void mixedText() {
        int mixed = estimator.estimate("你好 hello");
        // 2 CJK(≈2) + 6 ASCII(≈1.5) = 3.5 × 1.10 = 3.85 → ceil = 4
        assertEquals(4, mixed);
    }

    @Test
    void estimateMessagesCountsToolCalls() {
        List<Message> msgs = List.of(
                new Message("user", "abc"), // 3 ascii ≈ 0.75 ×1.1 ≈ 1
                Message.assistantWithTools(List.of(
                        new ToolCall("1", "read_file", "{\"path\":\"a.json\"}")))
        );
        int n = estimator.estimate(msgs);
        assertTrue(n > 0);
        // 至少覆盖了 assistant 消息内容（空串也计 0）+ 工具名 + 参数
        assertTrue(n >= 2);
    }

    // ---- 压缩保序 ----

    @Test
    void keepsThreeUserTurnsButCompactsOlder() {
        List<Message> history = new ArrayList<>();
        // 6 个 user 轮（中间穿插 assistant 与 tool 消息）
        for (int i = 1; i <= 6; i++) {
            history.add(new Message("user", "第" + i + "轮"));
            if (i % 3 == 0) {
                history.add(Message.assistantWithTools(List.of(
                        new ToolCall("c" + i, "write_file", "{\"path\":\"f" + i + ".txt\"}"))));
                history.add(Message.toolResult("c" + i, "已写入"));
            } else {
                history.add(new Message("assistant", "回答" + i));
            }
        }
        List<Message> comp = stubCompactor().compact(history);
        // 保留最近 2 个 user 轮：第 5、6 轮 + 其 assistant/tool 消息；前面压缩成 1 条摘要
        assertTrue(comp.size() < history.size());
        assertEquals("user", comp.get(0).role());
        assertTrue(comp.get(0).content().contains("摘要"));
        // 保序：第 5 轮在前、第 6 轮在后（user 文本原样保留）
        int i5 = indexOf(comp, "第5轮");
        int i6 = indexOf(comp, "第6轮");
        assertTrue(i5 >= 0 && i6 >= 0 && i5 < i6);
        // 早期第 1 轮被折叠进摘要，不再单独出现
        assertTrue(comp.stream().noneMatch(m -> "第1轮".equals(m.content())), "早期轮次不再单独保留");
    }

    private static int indexOf(List<Message> msgs, String content) {
        for (int i = 0; i < msgs.size(); i++) {
            if (content.equals(msgs.get(i).content())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void fewerThanThreeTurnsNotCompacted() {
        List<Message> history = List.of(
                new Message("user", "hi"),
                new Message("assistant", "yo"));
        List<Message> comp = stubCompactor().compact(history);
        assertEquals(2, comp.size());
    }

    // 摘要失败的降级路径：stub 抛异常时返回本地占位摘要，不爆窗
    @Test
    void compactFallsBackWhenLlmFails() {
        ChatClient failing = new ChatClient() {
            @Override public String call(List<Message> m) throws Exception { throw new RuntimeException("boom"); }
            @Override public LlmResponse call(List<Message> m, List<ToolDefinition> t) throws Exception { throw new RuntimeException(); }
            @Override public void callStream(List<Message> m, Consumer<String> onDelta) { }
        };
        HistoryCompactor c = new HistoryCompactor(failing);
        List<Message> history = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            history.add(new Message("user", "第" + i + "轮"));
            history.add(new Message("assistant", "回答" + i));
        }
        List<Message> comp = c.compact(history);
        assertTrue(comp.stream().allMatch(m -> m.content() != null));
        // 首条是降级占位摘要，含"省略"
        assertTrue(comp.get(0).content().contains("省略"));
    }

    private static HistoryCompactor stubCompactor() {
        return new HistoryCompactor(new SummarizingClient());
    }

    /** 摘要客户端：把传入内容回显成"摘要"前缀，保证非空。 */
    private static class SummarizingClient implements ChatClient {
        @Override public String call(List<Message> messages) {
            return "摘要：" + (messages.isEmpty() ? "" : messages.get(messages.size() - 1).content());
        }
        @Override public LlmResponse call(List<Message> m, List<ToolDefinition> t) throws Exception { throw new RuntimeException(); }
        @Override public void callStream(List<Message> m, Consumer<String> onDelta) { }
    }
}