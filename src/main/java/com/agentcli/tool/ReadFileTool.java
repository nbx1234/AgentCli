package com.agentcli.tool;

import com.agentcli.policy.PathGuard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * read_file：读取项目根目录内的文本文件。
 *
 * 超出根目录的路径由 PathGuard 拒绝；二进制文件与超长文件会被截断/拒绝。
 */
public class ReadFileTool implements Tool {

    private static final int MAX_BYTES = 8 * 1024;
    private static final int SNIFF = 1024;

    private final PathGuard guard;

    public ReadFileTool(File root) {
        this.guard = new PathGuard(root);
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "read_file",
                "读取项目根目录内的文本文件内容。参数 path 为相对或绝对路径；返回文件内容，" +
                        "超长（>8KB）或二进制文件会说明并截断。适合了解某个文件的内容。",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}");
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object pathVal = args.get("path");
        if (pathVal == null) {
            return "错误: 缺少参数 path";
        }
        try {
            File file = guard.resolve(pathVal.toString());
            if (!file.isFile()) {
                return "错误: 文件不存在: " + file.getPath();
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (isBinary(bytes)) {
                return "拒绝: " + file.getName() + " 看起来是二进制文件，不展示内容。";
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > MAX_BYTES) {
                return text.substring(0, MAX_BYTES) + "\n[truncated: 文件较长，仅显示前 8KB]";
            }
            return text;
        } catch (SecurityException e) {
            return "拒绝: " + e.getMessage();
        } catch (IOException e) {
            return "错误: 读取失败 " + e.getMessage();
        }
    }

    /** 前 1024 字节含 NUL 视为二进制。 */
    private static boolean isBinary(byte[] bytes) {
        int n = Math.min(bytes.length, SNIFF);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }
}