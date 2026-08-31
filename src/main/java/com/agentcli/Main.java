package com.agentcli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AgentCli 入口。
 *
 * Day 0：仅 Banner + 基础 REPL 循环，确认工具链贯通。
 * 后续 Day：接入 LLM client → ReAct 循环 → Web UI → 录制回放。
 */
public final class Main {

    static final String VERSION = "0.0.1";

    /** 测试可见入口，避免直接反射。 */
    static String versionForTest() {
        return VERSION;
    }

    /** 测试可见入口，避免直接反射。 */
    static String bannerForTest() {
        return BANNER;
    }
    private static final String BANNER = String.join("\n",
            "",
            "    ╔═╗┌─┐┌─┐┬─┐┌─┐┌┬─┐  ╦ ╦  ╦╔═╗╔╦╗",
            "    ║  ├─┘│ │├┬┘├─┤ ││  ║ ║  ║╚═╗ ║ ",
            "    ╚═╝┴  └─┘┴└─┴ ┴─┴┘  ╚═╝╚═╝╚═╝ ╩ ",
            "",
            "    Java Agent CLI · 可视化 ReAct + 录制回放即技能",
            "    v" + VERSION + " · Day 0 骨架",
            ""
    );

    private static final String PROMPT = "agent> ";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        System.out.print(BANNER);
        printTips();

        if (args.length > 0 && "--version".equals(args[0])) {
            System.out.println("agentcli " + VERSION);
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("\nBye.")));

        while (true) {
            System.out.print(PROMPT);
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            String input = line.trim();
            if (input.isEmpty()) {
                continue;
            }
            if (":q".equals(input) || ":quit".equals(input) || "exit".equals(input)) {
                return;
            }
            if (":version".equals(input)) {
                System.out.println("agentcli " + VERSION);
                continue;
            }
            if (":help".equals(input)) {
                printTips();
                continue;
            }
            // Day 0：直接回显，Day 1+ 接入 LLM
            System.out.println("[echo] " + input + "  (Day 0：LLM 接入从 Day 1 开始)");
        }
    }

    private static void printTips() {
        Map<String, String> tips = new LinkedHashMap<>();
        tips.put(":help", "显示本帮助");
        tips.put(":version", "查看版本");
        tips.put(":quit", "退出");
        tips.forEach((cmd, desc) -> System.out.printf("  %-10s · %s%n", cmd, desc));
        System.out.println();
    }
}
