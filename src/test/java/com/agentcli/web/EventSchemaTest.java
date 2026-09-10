package com.agentcli.web;

import com.agentcli.agent.Agent;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSchemaTest {

    /** 捕获 Agent 发的事件及其 payload，验证统一 schema。 */
    @Test
    void allEventsCarryTypeAndTs() throws Exception {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ToolDefinition("get_current_time", "时间", "{\"type\":\"object\"}"),
                args -> "now");
        Agent agent = new Agent(new FakeClient(), reg,
                (type, payload) -> {
                    assertTrue(type != null && !type.isBlank(), "type 必填");
                    Object ts = payload.get("ts");
                    assertNotNull(ts, "ts 必填");
                    assertTrue(ts instanceof Number && ((Number) ts).longValue() > 0, "ts 必须有效");
                });

        agent.run("现在几点", new ArrayList<>());

        // FakeClient 走到工具分支，需断言到 tool 事件（上面的回调已逐一校验）
    }

    @Test
    void llmEndCarriesDurationAndToolResultCarriesDuration() throws Exception {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ToolDefinition("get_current_time", "时间", "{\"type\":\"object\"}"),
                args -> "now");
        List<String> types = new ArrayList<>();
        List<Map<String, Object>> payloads = new ArrayList<>();
        Agent agent = new Agent(new FakeClient(), reg,
                (type, payload) -> {
                    types.add(type);
                    payloads.add(payload);
                });

        agent.run("现在几点", new ArrayList<>());

        int llmEndIdx = types.indexOf("llm_end");
        assertTrue(llmEndIdx >= 0);
        assertTrue(payloads.get(llmEndIdx).containsKey("durationMs"), "llm_end 应含 durationMs");

        int toolResultIdx = types.indexOf("tool_result");
        assertTrue(toolResultIdx >= 0);
        assertTrue(payloads.get(toolResultIdx).containsKey("durationMs"), "tool_result 应含 durationMs");
    }

    @Test
    void dagEndpointReturnsEmptyJson() throws Exception {
        WebSink sink = new WebSink();
        WebServer server = new WebServer(0, sink);
        try {
            server.start();
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + server.getPort() + "/api/dag").toURL().openConnection();
            assertEquals(200, conn.getResponseCode());
            String body = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().reduce("", String::concat);
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            assertTrue(root.get("nodes").isArray());
            assertTrue(root.get("edges").isArray());
            assertEquals(0, root.get("nodes").size());
            assertEquals(0, root.get("edges").size());
            conn.disconnect();
        } finally {
            server.stop();
        }
    }

    /** 先返回一次工具调用，再返回最终文本；携带 reasoning 用于 llm_end 预览。 */
    static class FakeClient implements ChatClient {
        private int calls = 0;

        @Override
        public String call(List<Message> messages) throws IOException {
            return call(messages, List.of()).content();
        }

        @Override
        public LlmResponse call(List<Message> messages, List<ToolDefinition> tools) {
            if (calls++ == 0) {
                return new LlmResponse("", List.of(new ToolCall("call_1", "get_current_time", "{}")), null);
            }
            return new LlmResponse("现在是 2026-09-05 14:30:00", List.of(), "思考中，先取时间再回答");
        }

        @Override
        public void callStream(List<Message> messages, java.util.function.Consumer<String> onDelta) {
            onDelta.accept("现在是 2026-09-05 14:30:00");
        }
    }
}