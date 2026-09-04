package com.agentcli.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeepSeekClientTest {

    private final DeepSeekClient client = new DeepSeekClient("test-key", "https://api.deepseek.com", "deepseek-chat");

    @Test
    void parsesNormalChoice() throws IOException {
        String json = """
                {"choices":[{"message":{"role":"assistant","content":"你好，很高兴认识你"}}]}""";
        assertEquals("你好，很高兴认识你", client.parseResponse(json));
    }

    @Test
    void rejectsMissingChoices() {
        String json = "{}";
        assertThrows(IOException.class, () -> client.parseResponse(json));
    }

    @Test
    void emptyContentIsAllowed() throws IOException {
        // 工具调用（tool_call）响应可能无 content，Day 4 起不再抛错
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}";
        assertEquals("", client.parseResponse(json));
    }

    @Test
    void buildsRequestBodyWithEscapedContent() {
        DeepSeekClient c = new DeepSeekClient("k", "https://api.deepseek.com/", "deepseek-chat");
        String body = c.buildRequestBody(java.util.List.of(new Message("user", "你好\"test\"\n新行")));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"model\":\"deepseek-chat\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"stream\":false"));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"role\":\"user\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("你好\\\"test\\\"\\n新行"));
    }

    @Test
    void marksRequestBodyAsStreaming() {
        DeepSeekClient c = new DeepSeekClient("k", "https://api.deepseek.com/", "deepseek-chat");
        String body = c.buildRequestBody(java.util.List.of(new Message("user", "hi")), true);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"stream\":true"));
    }

    @Test
    void buildsRequestBodyWithSystemAndHistory() {
        DeepSeekClient c = new DeepSeekClient("k", "https://api.deepseek.com/", "deepseek-chat");
        String body = c.buildRequestBody(java.util.List.of(
                new Message("system", "你是 AgentCli"),
                new Message("user", "我叫小明"),
                new Message("assistant", "你好，小明"),
                new Message("user", "我叫什么")));
        // system 与历史消息都应在 messages 中且按顺序出现
        int systemIdx = body.indexOf("\"role\":\"system\"");
        int user1Idx = body.indexOf("\"role\":\"user\",\"content\":\"我叫小明\"");
        int assistantIdx = body.indexOf("\"role\":\"assistant\"");
        int user2Idx = body.indexOf("\"role\":\"user\",\"content\":\"我叫什么\"");
        org.junit.jupiter.api.Assertions.assertTrue(systemIdx != -1);
        org.junit.jupiter.api.Assertions.assertTrue(user1Idx != -1);
        org.junit.jupiter.api.Assertions.assertTrue(assistantIdx != -1);
        org.junit.jupiter.api.Assertions.assertTrue(user2Idx != -1);
        org.junit.jupiter.api.Assertions.assertTrue(systemIdx < user1Idx);
        org.junit.jupiter.api.Assertions.assertTrue(user1Idx < assistantIdx);
        org.junit.jupiter.api.Assertions.assertTrue(assistantIdx < user2Idx);
    }
}