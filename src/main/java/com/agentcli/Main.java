package com.agentcli;

import com.agentcli.agent.Agent;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.DeepSeekClient;
import com.agentcli.llm.Message;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
            "    v" + VERSION + " · Day 5 ReAct 循环",
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
        Agent agent = null;
        try {
            llm = DeepSeekClient.fromEnv();
            agent = buildAgent(llm);
        } catch (IllegalStateException e) {
            System.out.println("[warn] " + e.getMessage());
            System.out.println("      请复制 .env.example 为 .env 并填入 DEEPSEEK_API_KEY 后再试。");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("\nBye.")));

        // Day 2：维护多轮对话历史（system prompt 每轮单独拼，不入 history）
        List<Message> history = new ArrayList<>();

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
            if ("/clear".equals(input)) {
                history.clear();
                System.out.println("[history] 已清空");
                continue;
            }
            if ("/history".equals(input)) {
                printHistory(history);
                continue;
            }
            // Day 1：接入 LLM 真实调用
            if (llm == null || agent == null) {
                System.out.println("[warn] 未配置 LLM，请先在 .env 填 DEEPSEEK_API_KEY");
                continue;
            }
            // Day 5：交给 Agent 走 ReAct 循环（内部处理历史、工具调用与答案）
            try {
                String reply = agent.run(input, history);
                System.out.println(reply);
            } catch (Exception e) {
                System.out.println("[error] " + e.getMessage());
            }
        }
    }

    private static void printHistory(List<Message> history) {
        System.out.println("[history] 共 " + history.size() + " 条");
        if (history.isEmpty()) {
            return;
        }
        int from = Math.max(0, history.size() - 3);
        for (int i = from; i < history.size(); i++) {
            Message m = history.get(i);
            System.out.printf("  %-9s %s%n", "[" + m.role() + "]", summarize(m.content()));
        }
    }

    /** 组装 Agent 并注册临时假工具（get_current_time），用于验证 ReAct 闭环。 */
    private static Agent buildAgent(ChatClient llm) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(
                new ToolDefinition("get_current_time", "获取当前日期与时间（东八区）",
                        "{\"type\":\"object\",\"properties\":{}}"),
                args -> LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString().replace('T', ' '));
        return new Agent(llm, registry);
    }

    private static String summarize(String content) {
        String oneLine = content.replace('\n', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 60) + "…";
    }

    private static void printTips() {
        Map<String, String> tips = new LinkedHashMap<>();
        tips.put(":help", "显示本帮助");
        tips.put(":version", "查看版本");
        tips.put(":quit", "退出");
        tips.put("/clear", "清空对话历史");
        tips.put("/history", "查看历史（条数与最近 3 条）");
        tips.forEach((cmd, desc) -> System.out.printf("  %-10s · %s%n", cmd, desc));
        System.out.println();
    }
}
