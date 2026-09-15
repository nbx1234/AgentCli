package com.agentcli.agent;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.prompt.SystemPrompt;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import com.agentcli.web.EventEmitter;
import com.agentcli.web.EventPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ReAct 循环：think → act → observe。
 *
 * 每轮：调 LLM → 若返回 tool_calls 则逐个执行、以 role=tool 结果回传、再调 LLM，
 * 直到某轮不再返回 tool_calls，此时 content 即最终答案。
 */
public class Agent {

    /** 安全阀：最多迭代轮数。 */
    static final int MAX_ITERATIONS = 10;

    /** tool 结果回传给 LLM 的最大长度，超出截断并标注。 */
    private static final int TOOL_RESULT_MAX = 8 * 1024;

    private final ChatClient client;
    private final ToolRegistry registry;
    private final EventEmitter events;

    public Agent(ChatClient client, ToolRegistry registry) {
        this(client, registry, EventEmitter.NOOP);
    }

    public Agent(ChatClient client, ToolRegistry registry, EventEmitter events) {
        this.client = client;
        this.registry = registry;
        this.events = events;
    }

    /**
     * 执行一轮用户输入。history 为跨轮复用（不含 system），本轮追加 user 与最终 assistant。
     *
     * @return 最终回答文本
     * @throws Exception LLM/网络失败；已回滚本轮构造的中间消息
     */
    public String run(String userInput, List<Message> history) throws Exception {
        int start = history.size();
        history.add(new Message("user", userInput));
        List<ToolDefinition> tools = registry.listDefinitions();

        try {
            int iteration = 0;
            events.emit("turn_start", EventPayload.create("turn_start").put("input", userInput).build());
            while (true) {
                if (++iteration > MAX_ITERATIONS) {
                    throw new IllegalStateException("达到最大迭代次数(" + MAX_ITERATIONS + ")，任务未完成");
                }
                List<Message> messages = new ArrayList<>();
                messages.add(new Message("system", SystemPrompt.build() + toolGuidance(tools)));
                messages.addAll(history);

                events.emit("llm_start", EventPayload.create("llm_start").iteration(iteration).build());
                long llmStart = System.nanoTime();
                LlmResponse resp = client.call(messages, tools);
                long llmMs = (System.nanoTime() - llmStart) / 1_000_000;
                String reasoning = (resp.reasoning() == null || resp.reasoning().isBlank()) ? null : resp.reasoning();
                events.emit("llm_end", EventPayload.create("llm_end").iteration(iteration)
                        .durationMs(llmMs).put("preview", reasoning).build());

                if (!resp.hasToolCalls()) {
                    String answer = resp.content();
                    history.add(new Message("assistant", answer));
                    emitAnswerDeltas(iteration, answer);
                    events.emit("turn_end", EventPayload.create("turn_end").iteration(iteration)
                            .put("answer", answer).put("preview", abbreviate(answer)).build());
                    return answer;
                }

                history.add(Message.assistantWithTools(resp.toolCalls()));
                for (ToolCall tc : resp.toolCalls()) {
                    String preview = abbreviate(tc.argumentsJson());
                    events.emit("tool_call", EventPayload.create("tool_call").iteration(iteration)
                            .put("tool", tc.name()).put("id", tc.id())
                            .put("args", tc.argumentsJson()).put("preview", preview).build());
                    long execStart = System.nanoTime();
                    String result = registry.execute(tc);
                    long execMs = (System.nanoTime() - execStart) / 1_000_000;
                    events.emit("tool_result", EventPayload.create("tool_result").iteration(iteration)
                            .put("tool", tc.name()).durationMs(execMs)
                            .put("result", truncate(result)).put("preview", firstLine(result)).build());
                    history.add(Message.toolResult(tc.id(), truncate(result)));
                }
            }
        } catch (Exception e) {
            // 调用失败：回滚本轮构造的所有消息，保持会话一致性
            while (history.size() > start) {
                history.remove(history.size() - 1);
            }
            throw e;
        }
    }

    /** 把最终回答切成若干片增量，逐条 emit answer_delta，让浏览器"逐字出现"。 */
    private void emitAnswerDeltas(int iteration, String answer) {
        if (answer == null || answer.isEmpty()) {
            return;
        }
        int chunk = Math.max(1, answer.length() / 48 + 1); // 控制事件数，避免 SSE 消息过多
        int step = Math.max(1, answer.length() / Math.min(50, chunk));
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < answer.length(); i += step) {
            acc.append(answer, i, Math.min(answer.length(), i + step));
            events.emit("answer_delta", EventPayload.create("answer_delta").iteration(iteration)
                    .put("text", acc.toString()).build());
            sleepMicro(40_000); // 每片 ~40ms，模拟打字节奏
        }
    }

    private static void sleepMicro(long nanos) {
        try {
            Thread.sleep(0, (int) nanos);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 供 LLM 参考的可工具名代理，说明可以借助这些工具完成任务。 */
    private static String toolGuidance(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        String names = tools.stream().map(ToolDefinition::name)
                .collect(Collectors.joining(", "));
        return "\n\n可用工具：" + names + "。需要读取/写文件或执行命令时，调用对应工具；拿到结果后再作答。";
    }

    /** 单行摘要：截断到 width 内并加省略号。 */
    static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ').trim();
        if (oneLine.length() <= 80) {
            return oneLine;
        }
        return oneLine.substring(0, 80) + "…";
    }

    /** 结果首行作为时间线预览。 */
    static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        String line = s.lines().findFirst().orElse("");
        return line.length() <= 100 ? line : line.substring(0, 100) + "…";
    }

    /** 避免 tool 结果过大撑爆上下文：>8KB 截断并注明。 */
    static String truncate(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= TOOL_RESULT_MAX) {
            return s;
        }
        return s.substring(0, TOOL_RESULT_MAX) + "\n[truncated: " + (s.length() - TOOL_RESULT_MAX) + " chars]";
    }
}