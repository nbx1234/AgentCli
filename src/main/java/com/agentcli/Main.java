package com.agentcli;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.DeepSeekClient;
import com.agentcli.llm.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
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

        // Day 1：预建 LLM 客户端；key 缺失时仍可启动，仅作提示，不阻塞。
        ChatClient llm = null;
        try {
            llm = DeepSeekClient.fromEnv();
        } catch (IllegalStateException e) {
            System.out.println("[warn] " + e.getMessage());
            System.out.println("      请复制 .env.example 为 .env 并填入 DEEPSEEK_API_KEY 后再试。");
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
            // Day 1：接入 LLM 真实调用
            if (llm == null) {
                System.out.println("[warn] 未配置 LLM，请先在 .env 填 DEEPSEEK_API_KEY");
                continue;
            }
            List<Message> messages = List.of(new Message("user", input));
            try {
                System.out.println(llm.call(messages));
            } catch (Exception e) {
                System.out.println("[error] " + e.getMessage());
            }
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
