package com.agentcli.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceRecorderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    @Test
    void writesMetaFirstThenEachEventAsJsonLine() throws Exception {
        Path dir = tmp.resolve("a");
        TraceRecorder recorder = TraceRecorder.openIn(dir, "deepseek-chat", "0.0.1");

        recorder.emit("turn_start", Map.of("type", "turn_start", "input", "帮我读 README"));
        recorder.emit("tool_call", Map.of("type", "tool_call", "tool", "read_file", "iteration", 1));
        recorder.emit("tool_result", Map.of("type", "tool_result", "tool", "read_file", "result", "ok"));
        recorder.close();

        List<String> lines = Files.readAllLines(recorder.file(), StandardCharsets.UTF_8);
        assertEquals(4, lines.size(), "首行 meta + 3 事件");

        // 首行是 meta，含 user 输入与模型、版本
        JsonNode meta = MAPPER.readTree(lines.get(0));
        assertEquals("meta", meta.get("type").asText());
        assertEquals("deepseek-chat", meta.get("model").asText());
        assertEquals("0.0.1", meta.get("version").asText());
        assertEquals("帮我读 README", meta.get("user").asText());

        // 后续事件顺序与类型一致（[0] meta，其后按写入顺序）
        assertEquals("turn_start", MAPPER.readTree(lines.get(1)).get("type").asText());
        assertEquals("tool_call", MAPPER.readTree(lines.get(2)).get("type").asText());
        assertEquals("tool_result", MAPPER.readTree(lines.get(3)).get("type").asText());
    }

    @Test
    void autoCreatesMissingDirectory() throws Exception {
        Path dir = tmp.resolve("nested/deep");
        TraceRecorder recorder = TraceRecorder.openIn(dir, "m", "v");
        assertTrue(Files.isDirectory(dir), "目录不存在应自动创建");
        recorder.close();
    }

    @Test
    void defaultDirIsUnderUserHome() {
        assertTrue(TraceRecorder.traceDir().toString().contains(System.getProperty("user.home")));
    }
}