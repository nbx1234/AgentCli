package com.agentcli.trace;

import com.agentcli.tool.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Day 14：/replay 回放的数据层——读一条 jsonl trace，按行解析成事件列表。
 *
 * 约定首行是 meta（由 {@link TraceRecorder} 保证），逐行的事件即同一天回放的时间线。
 * 干跑/实跑的策略在 Main 编排；本类只负责"读得到、可重建工具调用"。
 */
public final class TraceReplay {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 单条 trace 事件：type + 原始 JSON 节点，便于按需取值。 */
    public static final class Event {
        private final String type;
        private final JsonNode node;

        Event(String type, JsonNode node) {
            this.type = type;
            this.node = node;
        }

        public String type() {
            return type;
        }

        /** 取字符串字段，缺失返回空串。 */
        public String str(String key) {
            return node.path(key).asText("");
        }
    }

    private final Path file;
    private final List<Event> events;

    private TraceReplay(Path file, List<Event> events) {
        this.file = file;
        this.events = events;
    }

    /**
     * 读取 trace 文件。首行缺失或非法 JSON 视为损坏。
     *
     * @throws IOException 读取/解析失败
     */
    public static TraceReplay load(Path file) throws IOException {
        List<Event> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = MAPPER.readTree(line);
                } catch (IOException e) {
                    throw new IOException("第 " + (n + 1) + " 行不是合法 JSON: " + line, e);
                }
                out.add(new Event(node.path("type").asText("unknown"), node));
                n++;
            }
        }
        if (out.isEmpty()) {
            throw new IOException("空 trace: " + file);
        }
        return new TraceReplay(file, out);
    }

    public Path file() {
        return file;
    }

    public List<Event> events() {
        return events;
    }

    /** 首行 meta 的摘要，如 "录制于 2026-09-01 14:30"。 */
    public String metaLine() {
        Event meta = events.get(0);
        long ts = meta.node.path("ts").asLong(0L);
        if (ts == 0L) {
            return "录制于 <未知时间>";
        }
        String t = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                .withZone(ZONE).format(Instant.ofEpochMilli(ts));
        String src = meta.str("source");
        return (src.isEmpty() ? "" : "source=" + src + " ") + "录制于 " + t;
    }

    /** 从一条 tool_call 事件重建可执行的调用（回放不是特权通道，仍由 ToolRegistry 兜底）。 */
    public ToolCall asToolCall(Event ev) {
        return new ToolCall(ev.str("id"), ev.str("tool"), ev.str("args"));
    }

    /** 工具参数展示：优先完整 args，缺失退回单行 preview。 */
    public static String argsOf(Event ev) {
        String args = ev.str("args");
        if (!args.isBlank()) {
            return args;
        }
        String prev = ev.str("preview");
        return prev.isBlank() ? "{}" : prev;
    }
}