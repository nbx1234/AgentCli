package com.agentcli.trace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceReplayTest {

    @TempDir
    Path tmp;

    private Path write(String... lines) throws Exception {
        Path f = tmp.resolve("t.jsonl");
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void loadsMetaFirstThenEvents() throws Exception {
        Path f = write(
                "{\"type\":\"meta\",\"ts\":1,\"model\":\"m\"}",
                "{\"type\":\"tool_call\",\"tool\":\"read_file\",\"args\":\"{}\"}",
                "{\"type\":\"turn_end\",\"answer\":\"hi\"}");

        TraceReplay replay = TraceReplay.load(f);
        assertEquals(3, replay.events().size());
        assertEquals("meta", replay.events().get(0).type());
        assertEquals("tool_call", replay.events().get(1).type());
        assertEquals("turn_end", replay.events().get(2).type());
    }

    @Test
    void asToolCallRebuildsNameAndArgs() throws Exception {
        Path f = write(
                "{\"type\":\"meta\",\"ts\":1}",
                "{\"type\":\"tool_call\",\"id\":\"c1\",\"tool\":\"write_file\",\"args\":\"{\\\"path\\\":\\\"s\\\"}\"}");

        TraceReplay replay = TraceReplay.load(f);
        TraceReplay.Event ev = replay.events().get(1);
        assertEquals("write_file", replay.asToolCall(ev).name());
        assertEquals("{\"path\":\"s\"}", replay.asToolCall(ev).argumentsJson());
    }

    @Test
    void argsOfFallsBackToPreviewWhenNoFullArgs() throws Exception {
        Path f = write(
                "{\"type\":\"meta\",\"ts\":1}",
                "{\"type\":\"tool_call\",\"tool\":\"x\",\"preview\":\"seen preview\"}");

        TraceReplay replay = TraceReplay.load(f);
        assertEquals("seen preview", TraceReplay.argsOf(replay.events().get(1)));
    }

    @Test
    void metaLineFormatsTimeFromTs() throws Exception {
        Path f = write("{\"type\":\"meta\",\"ts\":1000}");
        assertEquals("录制于 1970-01-01 08:00", TraceReplay.load(f).metaLine());
    }

    @Test
    void loadRejectsBrokenJsonLine() throws Exception {
        Path f = write("{\"type\":\"meta\"}", "not json");
        assertThrows(Exception.class, () -> TraceReplay.load(f));
    }

    @Test
    void loadDoesNotWriteAnythingToDisk() throws Exception {
        long before;
        try (Stream<Path> s = Files.list(tmp)) {
            before = s.count();
        }
        write("{\"type\":\"meta\"}", "{\"type\":\"tool_call\",\"tool\":\"x\"}");
        TraceReplay.load(tmp.resolve("t.jsonl"));
        try (Stream<Path> s = Files.list(tmp)) {
            long after = s.count();
            assertEquals(before, after - 1, "load 只应新增我们写入的文件，不产生其他写入");
        }
    }
}