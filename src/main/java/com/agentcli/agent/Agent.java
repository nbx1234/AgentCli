package com.agentcli.agent;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.prompt.SystemPrompt;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;

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

    public Agent(ChatClient client, ToolRegistry registry) {
        this.client = client;
        this.registry = registry;
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
            while (true) {
                if (++iteration > MAX_ITERATIONS) {
                    throw new IllegalStateException("达到最大迭代次数(" + MAX_ITERATIONS + ")，任务未完成");
                }
                List<Message> messages = new ArrayList<>();
                messages.add(new Message("system", SystemPrompt.build()));
                messages.addAll(history);

                LlmResponse resp = client.call(messages, tools);
                if (!resp.hasToolCalls()) {
                    String answer = resp.content();
                    history.add(new Message("assistant", answer));
                    return answer;
                }

                history.add(Message.assistantWithTools(resp.toolCalls()));
                for (ToolCall tc : resp.toolCalls()) {
                    String result = registry.execute(tc);
                    System.out.println("⚡ tool: " + tc.name() + "(" + abbreviate(tc.argumentsJson()) + ")");
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

    private static String abbreviate(String argsJson) {
        if (argsJson == null || argsJson.length() <= 100) {
            return argsJson;
        }
        return argsJson.substring(0, 100) + "…";
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