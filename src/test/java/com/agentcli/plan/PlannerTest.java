package com.agentcli.plan;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlannerTest {

    /** 假客户端固定返回给定文本；记录调用次数以便断言重试。 */
    private static final class StaticClient implements ChatClient {
        final String body;
        int calls = 0;

        StaticClient(String body) {
            this.body = body;
        }

        @Override
        public String call(List<Message> messages) {
            calls++;
            return body;
        }

        @Override
        public LlmResponse call(List<Message> messages, List<ToolDefinition> tools) {
            return new LlmResponse(call(messages), List.of());
        }

        @Override
        public void callStream(List<Message> messages, Consumer<String> onDelta) {
            // 不走流式
        }
    }

    @Test
    void parsesPlainJson() throws Exception {
        String json = "{\"tasks\":["
                + "{\"id\":\"t1\",\"title\":\"调研\",\"prompt\":\"读 README\",\"dependsOn\":[]},"
                + "{\"id\":\"t2\",\"title\":\"产出\",\"prompt\":\"写文件\",\"dependsOn\":[\"t1\"]}]}";
        Planner planner = new Planner(new StaticClient(json));
        ExecutionPlan plan = planner.plan("调研", null);

        assertEquals(2, plan.tasks().size());
        assertEquals("t2", plan.tasks().get(1).id());
        assertEquals(List.of("t1"), plan.tasks().get(1).dependsOn());
    }

    @Test
    void stripsJsonFence() throws Exception {
        String json = "```json\n{\"tasks\":[{\"id\":\"t1\",\"title\":\"a\",\"prompt\":\"p\",\"dependsOn\":[]}]}\n```";
        Planner planner = new Planner(new StaticClient(json));
        assertEquals(1, planner.plan("x", null).tasks().size());
    }

    @Test
    void retriesOnceThenFailsOnRepeatedBadJson() throws Exception {
        StaticClient client = new StaticClient("not json at all");
        Planner planner = new Planner(client);
        assertThrows(Exception.class, () -> planner.plan("x", null));
        assertEquals(2, client.calls, "解析失败应重试 1 次");
    }

    @Test
    void emptyTasksIsRejectedAndRetried() throws Exception {
        String json = "{\"tasks\":[]}";
        StaticClient client = new StaticClient(json);
        Planner planner = new Planner(client);
        assertThrows(Exception.class, () -> planner.plan("x", null));
        assertEquals(2, client.calls, "空任务列表应触发重试");
    }
}