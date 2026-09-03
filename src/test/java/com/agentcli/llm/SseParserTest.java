package com.agentcli.llm;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SseParserTest {

    @Test
    void concatenatesContentDeltas() throws IOException {
        String sse = """
                data: {"choices":[{"delta":{"content":"你"}}]}
                data: {"choices":[{"delta":{"content":"好"}}]}
                data: {"choices":[{"delta":{"content":""}}]}
                data: {"choices":[{"delta":{"content":"世界"}}]}
                data: [DONE]
                """;
        assertEquals("你好世界", SseParser.collectDeltas(sse));
    }

    @Test
    void skipsFirstFrameWithoutContent() throws IOException {
        // 首帧只有 role，没有 content
        String sse = """
                data: {"choices":[{"delta":{"role":"assistant"}}]}
                data: {"choices":[{"delta":{"content":"Hi"}}]}
                """;
        assertEquals("Hi", SseParser.collectDeltas(sse));
    }

    @Test
    void ignoresBlanksAndDone() throws IOException {
        assertNull(SseParser.parseLine("data: [DONE]"));
        assertNull(SseParser.parseLine(""));
        assertNull(SseParser.parseLine("data: "));
        assertNull(SseParser.parseLine("not a data line"));
    }

    @Test
    void handlesNullContentInDelta() throws IOException {
        String sse = """
                data: {"choices":[{"delta":{"content":null}}]}
                data: {"choices":[{"delta":{"content":"x"}}]}
                """;
        assertEquals("x", SseParser.collectDeltas(sse));
    }
}