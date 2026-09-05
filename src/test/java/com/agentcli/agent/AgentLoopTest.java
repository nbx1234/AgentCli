package com.agentcli.agent;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopTest {

    /** 手写假 ChatClient：第一次返回 tool_call，第二次返回最终答案，并记录收到的历史。 */
    static class FakeClient implements ChatClient {
        final List<List<Message>> seen = new ArrayList<>();
        boolean toolCallAlready = false;

        @Override
        public String call(List<Message> messages) throws Exception {
            return call(messages, List.of()).content();
        }

        @Override
        public LlmResponse call(List<Message> messages, List<ToolDefinition> tools) throws Exception {
            seen.add(new ArrayList<>(messages));
            if (!toolCallAlready) {
                toolCallAlready = true;
                return new LlmResponse("", List.of(
                        new ToolCall("call_9", "get_current_time", "{}")));
            }
            return new LlmResponse("现在是 2026-09-05 14:30:00", List.of());
        }

        @Override
        public void callStream(List<Message> messages, Consumer<String> onDelta) {
            // 测试不走流式
        }
    }

    private Agent newAgent(FakeClient client) {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ToolDefinition("get_current_time", "获取当前时间", "{\"type\":\"object\"}"),
                args -> "2026-09-05 14:30:00");
        return new Agent(client, reg);
    }

    @Test
    void loopConvergesAndMessageSequenceIsCorrect() throws Exception {
        FakeClient fake = new FakeClient();
        Agent agent = newAgent(fake);
        List<Message> history = new ArrayList<>();

        String answer = agent.run("现在几点", history);

        assertEquals("现在是 2026-09-05 14:30:00", answer);
        // 两次 LLM 调用
        assertEquals(2, fake.seen.size());
        // 历史：user / assistant-with-tools / role=tool / assistant(final)
        assertEquals(4, history.size());
        assertEquals("user", history.get(0).role());
        assertEquals("assistant", history.get(1).role());
        assertEquals(1, history.get(1).toolCalls().size());
        assertEquals("call_9", history.get(1).toolCalls().get(0).id());
        assertEquals("tool", history.get(2).role());
        assertEquals("call_9", history.get(2).toolCallId());
        assertEquals("2026-09-05 14:30:00", history.get(2).content());
        assertEquals("assistant", history.get(3).role());
        // 每次调用的首条是 system
        assertTrue(fake.seen.get(0).get(0).role().equals("system"));
        assertTrue(fake.seen.get(1).get(0).role().equals("system"));
    }

    @Test
    void singleTurnWithoutToolsReturnsDirectly() throws Exception {
        class DirectClient extends FakeClient {
            @Override
            public LlmResponse call(List<Message> messages, List<ToolDefinition> tools) {
                seen.add(new ArrayList<>(messages));
                return new LlmResponse("你好", List.of());
            }
        }
        DirectClient fake = new DirectClient();
        Agent agent = newAgent(fake);
        List<Message> history = new ArrayList<>();

        assertEquals("你好", agent.run("你好", history));
        assertEquals(1, fake.seen.size());
        assertEquals(2, history.size()); // user + assistant
    }

    @Test
    void abortsWhenMaxIterationsReached() throws Exception {
        class LoopClient extends FakeClient {
            @Override
            public LlmResponse call(List<Message> messages, List<ToolDefinition> tools) {
                return new LlmResponse("", List.of(new ToolCall("call_x", "loop", "{}")));
            }
        }
        LoopClient fake = new LoopClient();
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ToolDefinition("loop", "循环", "{\"type\":\"object\"}"),
                args -> "again");
        Agent agent = new Agent(fake, reg);
        List<Message> history = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> agent.run("go", history));
        // 失败后回滚，history 恢复到进入前（空）
        assertEquals(0, history.size());
    }
}