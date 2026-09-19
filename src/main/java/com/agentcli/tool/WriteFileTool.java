package com.agentcli.tool;

import com.agentcli.policy.PathGuard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * write_file：写入/覆写项目根目录内的文件。
 *
 * 是否允许写入由 Day 18 的 HITL 审批层（Approver）在工具执行前裁定；
 * 本工具只负责路径护栏与写盘，不再内置 y/n 确认。
 */
public class WriteFileTool implements Tool {

    private final PathGuard guard;

    public WriteFileTool(File root) {
        this.guard = new PathGuard(root);
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "write_file",
                "写入或覆写项目根目录内的文件。参数 path 为文件路径，content 为完整文本。" +
                        "写入需用户审批，拒绝则不会写入。",
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}");
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object pathVal = args.get("path");
        Object contentVal = args.get("content");
        if (pathVal == null || contentVal == null) {
            return "错误: 缺少参数 path 或 content";
        }
        try {
            File file = guard.resolve(pathVal.toString());
            String content = contentVal.toString();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            return "已写入 " + content.getBytes(StandardCharsets.UTF_8).length + " 字节到 " + file.getPath();
        } catch (SecurityException e) {
            return "拒绝: " + e.getMessage();
        } catch (IOException e) {
            return "错误: 写入失败 " + e.getMessage();
        }
    }
}
