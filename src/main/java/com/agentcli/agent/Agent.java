package com.agentcli.agent;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.prompt.SystemPrompt;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import com.agentcli.web.EventEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            events.emit("turn_start", payload("input", userInput));
            while (true) {
                if (++iteration > MAX_ITERATIONS) {
                    throw new IllegalStateException("达到最大迭代次数(" + MAX_ITERATIONS + ")，任务未完成");
                }
                List<Message> messages = new ArrayList<>();
                messages.add(new Message("system", SystemPrompt.build() + toolGuidance(tools)));
                messages.addAll(history);

                events.emit("llm_call", payload("index", iteration));
                LlmResponse resp = client.call(messages, tools);
                if (!resp.hasToolCalls()) {
                    String answer = resp.content();
                    history.add(new Message("assistant", answer));
                    events.emit("turn_end", payload("answer", answer));
                    return answer;
                }

                history.add(Message.assistantWithTools(resp.toolCalls()));
                for (ToolCall tc : resp.toolCalls()) {
                    events.emit("tool_call", payload("tool", tc.name(), "args", tc.argumentsJson(), "id", tc.id()));
                    String result = registry.execute(tc);
                    events.emit("tool_result", payload("tool", tc.name(), "result", truncate(result)));
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

    /** 供 LLM 参考的可工具名代理，说明可以借助这些工具完成任务。 */
    private static String toolGuidance(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        String names = tools.stream().map(ToolDefinition::name)
                .collect(Collectors.joining(", "));
        return "\n\n可用工具：" + names + "。需要读取/写文件或执行命令时，调用对应工具；拿到结果后再作答。";
    }

    /** 构造事件 payload（保证键序，方便观看）。 */
    private static Map<String, Object> payload(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return m;
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