package com.agentcli.trace;

import com.agentcli.Env;
import com.agentcli.web.EventEmitter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Day 13：把 Agent 过程事件逐行落盘为 JSONL（录制回放的地基）。
 *
 * 与 Web 事件共用同一 schema——挂在同一 EventEmitter 上即可零成本录制。
 * 首行写 meta（用户首个输入、模型、版本），之后每个事件一行 JSON。
 * 目录默认 {@code ~/.agentcli/traces}，可用环境变量 {@code AGENTCLI_TRACE_DIR} 覆盖。
 */
public final class TraceRecorder implements EventEmitter {

    /** meta 首行写入后才开始记事件，保证文件首行永远是 meta。 */
    private static final DateTimeFormatter NAME_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 单条事件 JSON 键与值的预览截断上限（落盘够用，完整内容在文件系统里）。 */
    private static final int LINE_MAX = 64 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final BufferedWriter writer;
    private final String model;
    private final String version;
    /** 非空表示这是一条 replay 录制：首行 meta 应标记 type=replay + source=<原id>。 */
    private final String replaySource;
    /** 非空表示这是一条技能执行录制：meta 记 type=skill + skill=<名>。 */
    private final String skillName;
    private boolean metaWritten = false;

    private TraceRecorder(Path file, BufferedWriter writer, String model, String version, String replaySource,
                          String skillName) {
        this.file = file;
        this.writer = writer;
        this.model = model;
        this.version = version;
        this.replaySource = replaySource;
        this.skillName = skillName;
    }

    /**
     * 打开一个新的 trace 文件（自动建目录；文件名带秒 + 冲突后缀防同秒并发）。
     *
     * @param model   模型名，写入 meta
     * @param version 应用版本，写入 meta
     */
    public static TraceRecorder open(String model, String version) throws IOException {
        return openIn(traceDir(), model, version);
    }

    /** 打开于指定目录（测试/工具注入用）。 */
    static TraceRecorder openIn(Path dir, String model, String version) throws IOException {
        return openIn(dir, model, version, null);
    }

    /** 打开一条 replay 录制：meta 记 type=replay + source=<原 trace id>。 */
    public static TraceRecorder openReplay(String sourceId, String model, String version) throws IOException {
        return openIn(traceDir(), model, version, sourceId, null);
    }

    /** 打开一条技能执行录制：meta 记 type=skill + skill=<技能名>。 */
    public static TraceRecorder openSkill(String skillName, String model, String version) throws IOException {
        return openIn(traceDir(), model, version, null, skillName);
    }

    private static TraceRecorder openIn(Path dir, String model, String version, String replaySource)
            throws IOException {
        return openIn(dir, model, version, replaySource, null);
    }

    private static TraceRecorder openIn(Path dir, String model, String version, String replaySource,
                                        String skillName) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(newTraceName(dir));
        BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        return new TraceRecorder(file, w, model, version, replaySource, skillName);
    }

    public Path file() {
        return file;
    }

    /** 关闭写句柄；未能写入的缓冲不强制落盘（逐事件已 flush）。 */
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public void emit(String type, Map<String, Object> payload) {
        try {
            if (!metaWritten) {
                writeMeta(payload.containsKey("input") ? String.valueOf(payload.get("input")) : "");
                metaWritten = true;
            }
            writeJson(MAPPER.writeValueAsString(payload));
        } catch (IOException e) {
            // 录制失败丢弃事件，不影响主流程
        }
    }

    private void writeMeta(String userInput) throws IOException {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("ts", System.currentTimeMillis());
        meta.put("model", model);
        meta.put("version", version);
        if (replaySource != null) {
            meta.put("type", "replay");
            meta.put("source", replaySource);
        } else if (skillName != null) {
            meta.put("type", "skill");
            meta.put("skill", skillName);
        } else {
            meta.put("type", "meta");
            meta.put("user", truncate(userInput));
        }
        writeJson(MAPPER.writeValueAsString(meta));
    }

    private void writeJson(String line) throws IOException {
        String safe = line.length() <= LINE_MAX ? line : line.substring(0, LINE_MAX) + "…";
        writer.write(safe);
        writer.newLine();
        writer.flush();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    /** Trace 根目录：AGENTCLI_TRACE_DIR 覆盖，默认 ~/.agentcli/traces。 */
    public static Path traceDir() {
        String dir = Env.get("AGENTCLI_TRACE_DIR");
        if (dir != null && !dir.isBlank()) {
            return Path.of(dir);
        }
        return Path.of(System.getProperty("user.home"), ".agentcli", "traces");
    }

    private static String newTraceName(Path dir) throws IOException {
        String base = LocalDateTime.now(ZONE).format(NAME_FMT);
        String name = base + ".jsonl";
        int seq = 1;
        while (Files.exists(dir.resolve(name))) {
            name = base + "-" + (++seq) + ".jsonl";
        }
        return name;
    }

    /** 列出所有 trace 文件，每文件首行（meta）摘要。 */
    public static void list() throws IOException {
        Path dir = traceDir();
        if (!Files.isDirectory(dir)) {
            System.out.println("[trace] 目录不存在: " + dir.resolve("").normalize());
            return;
        }
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (files.isEmpty()) {
            System.out.println("[trace] 暂无录制: " + dir.resolve("").normalize());
            return;
        }
        System.out.println("[trace] " + files.size() + " 条录制于 " + dir.resolve("").normalize() + ":");
        for (Path p : files) {
            System.out.printf("  %-24s %s%n", p.getFileName(), metaSummary(p));
        }
    }

    /** 打印单条 trace 全文（人类可读）。id 为文件名（可带 .jsonl）。 */
    public static void show(String id) throws IOException {
        if (id.isBlank()) {
            System.out.println("[trace] 用法: /trace show <id>");
            return;
        }
        Path dir = traceDir();
        String name = id.endsWith(".jsonl") ? id : id + ".jsonl";
        Path file = dir.resolve(name);
        if (!Files.isRegularFile(file)) {
            System.out.println("[trace] 未找到: " + name);
            return;
        }
        System.out.println("[trace] " + name + ":");
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int n = 0;
            while ((line = br.readLine()) != null) {
                n++;
                System.out.printf("  %3d  %s%n", n, pretty(line));
            }
        }
    }

    private static String metaSummary(Path p) {
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String first = br.readLine();
            if (first == null) {
                return "(empty)";
            }
            JsonNode m = MAPPER.readTree(first);
            String user = m.path("user").asText("");
            String ts = m.path("ts").asText("");
            return (user.isEmpty() ? "-" : truncate(user)) + "  ts=" + ts;
        } catch (IOException e) {
            return "(unreadable)";
        }
    }

    private static String pretty(String line) {
        try {
            return MAPPER.readTree(line).toString();
        } catch (Exception e) {
            return truncate(line);
        }
    }
}