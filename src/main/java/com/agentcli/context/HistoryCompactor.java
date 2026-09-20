package com.agentcli.context;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Day 19：上下文压缩器。
 *
 * 保留 system + 最近 2 个 user 轮次完整内容，把更早的部分交给 LLM 一次性摘要成 1 条消息替换。
 * 中途的 tool_call / tool_result 成对折叠进摘要（"期间调用了 X/Y/Z，结论是…"）。
 *
 * 摘要用非流式 {@link ChatClient#call}；失败时降级为保留最早一条 + 丢弃中段，保证不爆窗。
 */
public final class HistoryCompactor {

    /** 保留的最近 user 轮次数。 */
    private static final int KEEP_USER_TURNS = 2;

    /** 摘要输出上限，避免摘要本身占太多窗口。 */
    private static final int SUMMARY_MAX = 400;

    private final ChatClient client;
    private final TokenEstimator estimator = new TokenEstimator();

    public HistoryCompactor(ChatClient client) {
        this.client = client;
    }

    /**
     * 压缩 history（不含 system；Agent 每轮自行拼接）。
     *
     * @return 压缩后的新列表（不改动入参列表）
     */
    public List<Message> compact(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }
        List<Integer> userIdx = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            if ("user".equals(history.get(i).role())) {
                userIdx.add(i);
            }
        }
        if (userIdx.size() <= KEEP_USER_TURNS) {
            return new ArrayList<>(history);
        }
        int cut = userIdx.get(userIdx.size() - KEEP_USER_TURNS); // 首个保留 user 的索引
        List<Message> old = history.subList(0, cut);
        List<Message> kept = history.subList(cut, history.size());

        String summary = summarizeOld(old);
        List<Message> out = new ArrayList<>();
        out.add(new Message("user", summary));
        out.addAll(kept);
        return out;
    }

    /** 把中段压缩成一段文字；LLM 失败时用本地降级。 */
    private String summarizeOld(List<Message> old) {
        String text = flatten(old);
        String prompt = "下面是 Agent 早期的一段执行对话。请用不超过 200 字概括其要点，"
                + "保留影响后续的关键信息：做过的决策、写入过哪些文件、执行过哪些命令及其结论、用户给出的要求。"
                + "只输出概括本身，不要任何其他文字。\n\n" + text;
        try {
            String summary = client.call(List.of(new Message("user", prompt)));
            if (summary != null && !summary.isBlank()) {
                return summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX) + "…";
            }
        } catch (Exception ignored) {
            // 摘要失败 → 降级
        }
        return "(早期对话摘要失败，已省略 " + old.size() + " 条消息；如需早期细节请重新说明)";
    }

    /** 把消息序列摊平成单段可读文本（工具调用折叠成工具名列表）。 */
    private String flatten(List<Message> msgs) {
        StringBuilder sb = new StringBuilder();
        for (Message m : msgs) {
            switch (m.role()) {
                case "user" -> sb.append("user: ").append(firstLine(m.content())).append('\n');
                case "assistant" -> {
                    if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                        List<String> names = m.toolCalls().stream()
                                .map(com.agentcli.tool.ToolCall::name).toList();
                        sb.append("assistant 调用了工具: ").append(String.join(", ", names)).append('\n');
                    } else {
                        sb.append("assistant: ").append(firstLine(m.content())).append('\n');
                    }
                }
                case "tool" -> sb.append("tool 结果: ").append(firstLine(m.content())).append('\n');
                default -> { }
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String firstLine(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String l = s.lines().findFirst().orElse("").trim();
        int cap = 120;
        return l.length() <= cap ? l : l.substring(0, cap) + "…";
    }

    /** 当前保序的保留轮数（供测试/日志参考）。 */
    public static int keepUserTurns() {
        return KEEP_USER_TURNS;
    }
}