package com.agentcli.policy;

import com.agentcli.Env;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Day 18：审计日志。每行一条 JSON，只追加不修改：
 * {@code ts / tool / args / decision（ALLOW|DENY）/ source（policy|user|session）}。
 *
 * 默认 {@code ~/.agentcli/audit.jsonl}，可用环境变量 {@code AGENTCLI_AUDIT_FILE} 覆盖。
 */
public final class AuditLog implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 单条 args 落盘截断上限（完整内容在 trace/面板里）。 */
    private static final int ARGS_MAX = 200;

    private final Path file;
    private final BufferedWriter writer;

    /** 打开指定文件（测试可注入临时路径）。 */
    public AuditLog(Path file) throws IOException {
        this.file = file;
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** 打开默认审计文件（AGENTCLI_AUDIT_FILE 覆盖）。 */
    public static AuditLog openDefault() throws IOException {
        return new AuditLog(defaultFile());
    }

    /** 默认审计文件路径。 */
    public static Path defaultFile() {
        String v = Env.get("AGENTCLI_AUDIT_FILE");
        if (v != null && !v.isBlank()) {
            return Path.of(v);
        }
        return Path.of(System.getProperty("user.home"), ".agentcli", "audit.jsonl");
    }

    /** 追加一条审计记录；写失败只打 stderr，不打断主流程。 */
    public void record(String tool, String args, String decision, String source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ts", System.currentTimeMillis());
        row.put("tool", tool);
        row.put("args", truncate(args));
        row.put("decision", decision);
        row.put("source", source);
        try {
            writer.write(MAPPER.writeValueAsString(row));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[audit] 写入失败: " + e.getMessage());
        }
    }

    public Path file() {
        return file;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    /** 读取最近 n 条（读默认文件；文件不存在返回空列表）。 */
    public static List<Map<String, Object>> recent(int n) throws IOException {
        return read(defaultFile(), n);
    }

    /** 读取指定文件最近 n 条（测试用）。 */
    public static List<Map<String, Object>> read(Path file, int n) throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<Map<String, Object>> all = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    all.add(MAPPER.readValue(line, new TypeReference<Map<String, Object>>() { }));
                } catch (IOException ignored) {
                    // 半行/损坏记录跳过，不影响其余
                }
            }
        }
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= ARGS_MAX ? s : s.substring(0, ARGS_MAX) + "…";
    }
}
