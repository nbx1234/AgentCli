package com.agentcli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRpcTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void encodeRequestCarriesVersionIdAndMethod() throws Exception {
        String json = McpClient.encodeRequest("initialize", MAPPER.createObjectNode(), 1);
        JsonNode n = MAPPER.readTree(json);
        assertEquals("2.0", n.get("jsonrpc").asText());
        assertEquals(1, n.get("id").asInt());
        assertEquals("initialize", n.get("method").asText());
        assertTrue(n.has("params"));
    }

    @Test
    void encodeNotificationOmitsId() throws Exception {
        String json = McpClient.encodeRequest("notifications/initialized", null, 0);
        JsonNode n = MAPPER.readTree(json);
        assertTrue(!n.has("id"), "通知不应带 id");
        assertEquals("notifications/initialized", n.get("method").asText());
    }

    @Test
    void parseResponseAcceptsValidAndRejectsWrongVersion() throws Exception {
        JsonNode ok = McpClient.parseResponse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
        assertEquals(1, ok.get("id").asInt());

        assertThrows(Exception.class,
                () -> McpClient.parseResponse("{\"jsonrpc\":\"1.0\",\"id\":1,\"result\":{}}"));
        assertThrows(Exception.class,
                () -> McpClient.parseResponse("not json at all"));
    }

    @Test
    void mcpToolNamePrefixing() {
        assertEquals("mcp__echo__add", McpServerManager.toolName("echo", "add"));
    }
}