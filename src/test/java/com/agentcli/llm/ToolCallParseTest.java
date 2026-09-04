package com.agentcli.llm;

import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallParseTest {

    private final DeepSeekClient client = new DeepSeekClient("test-key", "https://api.deepseek.com", "deepseek-chat");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesMultipleToolCalls() throws IOException {
        String json = """
                {"choices":[{"message":{
                  "role":"assistant",
                  "content":null,
                  "tool_calls":[
                    {"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\\"path\\":\\"README.md\\"}"}},
                    {"id":"call_2","type":"function","function":{"name":"list_dir","arguments":"{\\"path\\":\\".\\"}"}}
                  ]}}]}""";
        LlmResponse resp = client.parseLlmResponse(json);
        assertTrue(resp.hasToolCalls());
        assertEquals(2, resp.toolCalls().size());

        ToolCall first = resp.toolCalls().get(0);
        assertEquals("call_1", first.id());
        assertEquals("read_file", first.name());
        assertEquals("{\"path\":\"README.md\"}", first.argumentsJson());

        ToolCall second = resp.toolCalls().get(1);
        assertEquals("call_2", second.id());
        assertEquals("list_dir", second.name());
    }

    @Test
    void argumentsAreParsedIntoMap() throws IOException {
        String arguments = "{\"path\":\"README.md\",\"lines\":10}";
        Map<String, Object> map = MAPPER.readValue(arguments, new TypeReference<Map<String, Object>>() {});
        assertEquals("README.md", map.get("path"));
        assertEquals(10, ((Number) map.get("lines")).intValue());
    }

    @Test
    void returnsEmptyContentWhenNoToolCalls() throws IOException {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"普通回答\"}}]}";
        LlmResponse resp = client.parseLlmResponse(json);
        assertEquals("普通回答", resp.content());
        assertTrue(resp.toolCalls().isEmpty());
    }

    // ----- 工具定义/注册表 / 请求体序列化 -----

    @Test
    void toolDefinitionSerializesToOpenAiFormat() {
        ToolDefinition def = new ToolDefinition("read_file", "读文件",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
        String j = def.toToolsJson();
        assertTrue(j.contains("\"type\":\"function\""));
        assertTrue(j.contains("\"name\":\"read_file\""));
        assertTrue(j.contains("\"parameters\""));
    }

    @Test
    void registryRegistersAndLists() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ToolDefinition("read_file", "读文件",
                "{\"type\":\"object\"}"));
        reg.register(new ToolDefinition("list_dir", "列目录",
                "{\"type\":\"object\"}"));
        assertEquals("read_file", reg.lookup("read_file").name());
        assertNull(reg.lookup("nope"));
        assertEquals(2, reg.listDefinitions().size());
    }

    @Test
    void requestBodyCarriesToolsAndToolMessages() {
        DeepSeekClient c = new DeepSeekClient("k", "https://api.deepseek.com/", "deepseek-chat");
        ToolDefinition def = new ToolDefinition("read_file", "读文件", "{\"type\":\"object\"}");
        List<Message> msgs = List.of(
                new Message("user", "读 README"),
                Message.assistantWithTools(List.of(new ToolCall("call_1", "read_file", "{\"path\":\"README.md\"}"))),
                Message.toolResult("call_1", "文件内容")
        );
        String body = c.buildRequestBody(msgs, false, List.of(def));
        assertTrue(body.contains("\"tools\":[{"));
        assertTrue(body.contains("\"name\":\"read_file\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"tool_calls\""));
        assertTrue(body.contains("\"role\":\"tool\""));
        assertTrue(body.contains("\"tool_call_id\":\"call_1\""));
    }
}