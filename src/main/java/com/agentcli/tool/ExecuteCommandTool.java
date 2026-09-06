package com.agentcli.tool;

import com.agentcli.policy.CommandGuard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * execute_command：在项目根目录用 bash 执行一条命令，超时 60s，输出截断。
 *
 * 危险命令由 CommandGuard 拦截；stderr 与 stdout 合并返回。
 */
public class ExecuteCommandTool implements Tool {

    private static final int MAX_OUTPUT = 8 * 1024;
    private static final long TIMEOUT_SECONDS = 60;

    private final File workDir;

    public ExecuteCommandTool(File workDir) {
        this.workDir = workDir;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "execute_command",
                "在项目根目录执行一条 shell 命令并返回输出（stdout+stderr，最多 8KB）。" +
                        "命令超时 60s。适合运行测试、查看网络、处理文件。危险命令会被安全护栏拦截。",
                "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}");
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object cmdVal = args.get("command");
        if (cmdVal == null) {
            return "错误: 缺少参数 command";
        }
        String command = cmdVal.toString().trim();
        try {
            CommandGuard.check(command);
        } catch (SecurityException e) {
            return "被拦截: " + e.getMessage();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.directory(workDir);
            pb.redirectErrorStream(true); // merge stderr
            Process process = pb.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "命令超时（>60s），已终止。";
            }
            String output = readFully(process);
            if (output.length() > MAX_OUTPUT) {
                output = output.substring(0, MAX_OUTPUT) + "\n[truncated: 输出较长，仅显示前 8KB]";
            }
            return output.isEmpty() ? "(无输出，退出码 " + process.exitValue() + ")" : output;
        } catch (IOException e) {
            return "错误: 无法执行命令 " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "命令被中断";
        }
    }

    private static String readFully(Process process) {
        try (java.util.Scanner sc = new java.util.Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
            sc.useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        }
    }
}