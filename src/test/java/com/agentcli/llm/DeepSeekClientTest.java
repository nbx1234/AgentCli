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
    void rejectsMissingContent() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}";
        assertThrows(IOException.class, () -> client.parseResponse(json));
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
}