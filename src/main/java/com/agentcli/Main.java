package com.agentcli;

import com.agentcli.agent.Agent;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.DeepSeekClient;
import com.agentcli.llm.Message;
import com.agentcli.tool.ExecuteCommandTool;
import com.agentcli.tool.ReadFileTool;
import com.agentcli.tool.ToolRegistry;
import com.agentcli.tool.WriteFileTool;
import com.agentcli.web.ConsoleSink;
import com.agentcli.web.EventEmitter;
import com.agentcli.web.WebServer;
import com.agentcli.web.WebSink;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            "    v" + VERSION + " · Day 7 Web+SSE",
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
        // Day 7：`--web` 时启动本地 Web 服务，把 Agent 事件流打到浏览器。
        ChatClient llm = null;
        Agent agent = null;
        WebServer[] webServerRef = new WebServer[1];
        try {
            llm = DeepSeekClient.fromEnv();
            EventEmitter emitter = new ConsoleSink();
            if (containsFlag(args, "--web")) {
                WebSink web = new WebSink();
                EventEmitter console = emitter;
                emitter = (t, p) -> {
                    console.emit(t, p);
                    web.emit(t, p);
                };
                try {
                    WebServer server = new WebServer(webPort(), web);
                    server.start();
                    webServerRef[0] = server;
                    System.out.println("[web] 事件流就绪 → http://127.0.0.1:" + server.getPort() + "/api/events");
                } catch (Exception e) {
                    // 端口被占等启动失败：降级为纯 CLI，不阻塞
                    System.out.println("[warn] Web 启动失败，降级为 CLI 模式: " + e.getMessage());
                }
            }
            agent = buildAgent(llm, emitter);
        } catch (IllegalStateException e) {
            System.out.println("[warn] " + e.getMessage());
            System.out.println("      请复制 .env.example 为 .env 并填入 DEEPSEEK_API_KEY 后再试。");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (webServerRef[0] != null) {
                webServerRef[0].stop();
            }
            System.out.println("\nBye.");
        }));

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

    /** 组装 Agent 并注册内置工具（读写文件 + 执行命令），走路径/命令护栏。 */
    private static Agent buildAgent(ChatClient llm, EventEmitter emitter) throws IOException {
        File root = Env.rootPath().toFile();
        BufferedReader prompt = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(root));
        registry.register(new WriteFileTool(root, prompt));
        registry.register(new ExecuteCommandTool(root));
        return new Agent(llm, registry, emitter);
    }

    /** 命令行是否含某 flag。 */
    private static boolean containsFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    /** Web 端口：上限 AGENTCLI_WEB_PORT，默认 8080；非法值回落默认。 */
    private static int webPort() {
        String v = Env.get("AGENTCLI_WEB_PORT");
        if (v != null && !v.isBlank()) {
            try {
                int p = Integer.parseInt(v.trim());
                if (p > 0 && p < 65536) {
                    return p;
                }
            } catch (NumberFormatException ignored) {
                // 回落默认
            }
        }
        return 8080;
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
