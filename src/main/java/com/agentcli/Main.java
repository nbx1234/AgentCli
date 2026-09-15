package com.agentcli;

import com.agentcli.agent.Agent;
import com.agentcli.agent.SubAgent;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.DeepSeekClient;
import com.agentcli.llm.Message;
import com.agentcli.plan.ExecutionPlan;
import com.agentcli.plan.PlanExecutor;
import com.agentcli.plan.PlanReviewParser;
import com.agentcli.plan.Planner;
import com.agentcli.plan.Task;
import com.agentcli.tool.ExecuteCommandTool;
import com.agentcli.tool.ReadFileTool;
import com.agentcli.tool.ToolRegistry;
import com.agentcli.tool.WriteFileTool;
import com.agentcli.trace.TraceRecorder;
import com.agentcli.trace.TraceReplay;
import com.agentcli.web.ConsoleSink;
import com.agentcli.web.EventEmitter;
import com.agentcli.web.WebServer;
import com.agentcli.web.WebSink;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            "    v" + VERSION + " · Day 13 Trace 录制",
            ""
    );

    private static final String PROMPT = "agent> ";

    /** Day 12：补充重规划上限，防 i 无限循环。 */
    private static final int MAX_REPLAN = 3;

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
        // Day 13：Trace 落盘与 Console/Web 三路广播，每会话一个 JSONL 文件。
        ChatClient llm = null;
        Agent agent = null;
        SubAgent subAgent = null;
        ToolRegistry registry = null;
        WebServer[] webServerRef = new WebServer[1];
        TraceRecorder[] traceRef = new TraceRecorder[1];
        EventEmitter[] emitterRef = new EventEmitter[1];
        try {
            llm = DeepSeekClient.fromEnv();
            EventEmitter console = new ConsoleSink();
            WebSink webSink = null;
            if (containsFlag(args, "--web")) {
                webSink = new WebSink();
                try {
                    WebServer server = new WebServer(webPort(), webSink);
                    server.start();
                    webServerRef[0] = server;
                    System.out.println("[web] 事件流就绪 → http://127.0.0.1:" + server.getPort() + "/api/events");
                } catch (Exception e) {
                    // 端口被占等启动失败：降级为纯 CLI，不阻塞
                    webSink = null;
                    System.out.println("[warn] Web 启动失败，降级为 CLI 模式: " + e.getMessage());
                }
            }
            EventEmitter sink;
            WebSink ws = webSink;
            if (ws == null) {
                sink = console;
            } else {
                EventEmitter base = console;
                sink = (t, p) -> {
                    base.emit(t, p);
                    ws.emit(t, p);
                };
            }
            TraceRecorder trace;
            try {
                trace = TraceRecorder.open("deepseek-chat", VERSION);
                traceRef[0] = trace;
            } catch (IOException te) {
                trace = null;
                System.out.println("[warn] Trace 目录不可用，本次不录制: " + te.getMessage());
            }
            EventEmitter emitter = (t, p) -> {
                sink.emit(t, p);
                TraceRecorder tr = traceRef[0];
                if (tr != null) {
                    tr.emit(t, p);
                }
            };
            registry = buildRegistry();
            emitterRef[0] = emitter;
            agent = new Agent(llm, registry, emitter);
            subAgent = new SubAgent(llm, registry, emitter);
        } catch (IllegalStateException e) {
            System.out.println("[warn] " + e.getMessage());
            System.out.println("      请复制 .env.example 为 .env 并填入 DEEPSEEK_API_KEY 后再试。");
        }
        // Trace 目录不可用等异常到此已单独降级，主流程仍可继续。

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (webServerRef[0] != null) {
                webServerRef[0].stop();
            }
            if (traceRef[0] != null) {
                try {
                    traceRef[0].close();
                } catch (IOException ignored) {
                }
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
            // Day 13：/trace 不走 LLM，独立于对话历史解析
            if (input.startsWith("/trace")) {
                runTraceCommand(input);
                continue;
            }
            // Day 14：/replay 回放一条 trace；--apply 才真执行工具
            if (input.startsWith("/replay")) {
                runReplayFlow(reader, registry, emitterRef[0], input);
                continue;
            }
            // Day 10-12：/plan 生成 → 审阅 → 执行
            if (input.startsWith("/plan")) {
                if (llm == null || subAgent == null) {
                    System.out.println("[warn] 未配置 LLM，无法规划");
                    continue;
                }
                runPlanFlow(reader, llm, subAgent, emitterRef[0], webServerRef[0], history, input);
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

    /** 组装工具注册表（读写文件 + 执行命令），走路径/命令护栏。 */
    private static ToolRegistry buildRegistry() throws IOException {
        File root = Env.rootPath().toFile();
        BufferedReader prompt = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(root));
        registry.register(new WriteFileTool(root, prompt));
        registry.register(new ExecuteCommandTool(root));
        return registry;
    }

    /**
     * Day 10-12：生成计划 → 审阅（r/i/c，最多重规划 3 次）→ 执行。
     * 审阅不写主对话 history；仅执行完成后把结果摘要并入，供后续引用。
     */
    private static void runPlanFlow(BufferedReader reader, ChatClient llm, SubAgent subAgent,
                                EventEmitter emitter, WebServer webServer, List<Message> history,
                                String input) throws IOException {
        String goal = input.substring("/plan".length()).trim();
        if (goal.isEmpty()) {
            System.out.println("[plan] 用法: /plan <目标>，随后 [r]执行 / [i]补充要求 / [c]取消。");
            System.out.println("[plan] 生成后按拓扑序展示任务，审阅通过才真正执行。");
            return;
        }
        Planner planner = new Planner(llm);
        ExecutionPlan plan = null;
        String extra = null;
        int replans = 0;
        while (true) {
            try {
                System.out.println("[plan] 正在生成任务计划…");
                plan = planner.plan(goal, extra);
            } catch (Exception e) {
                System.out.println("[plan] 计划生成失败：" + e.getMessage());
                return;
            }
            printPlan(plan);
            System.out.print("[r]执行  [i]补充要求  [c]取消  > ");
            String choice = reader.readLine();
            PlanReviewParser.Choice c = PlanReviewParser.parseChoice(choice);
            if (c == null) {
                System.out.println("[plan] 无法识别的输入，请键入 r / i / c。");
                continue;
            }
            if (c == PlanReviewParser.Choice.CANCEL) {
                System.out.println("[plan] 已取消，回到普通对话。");
                return;
            }
            if (c == PlanReviewParser.Choice.RUN) {
                execute(plan, subAgent, emitter, webServer, history);
                return;
            }
            // REFINE：读补充要求，连同原目标重新规划
            if (replans >= MAX_REPLAN) {
                System.out.println("[plan] 已到最大重规划次数(" + MAX_REPLAN + ")，直接执行当前计划。");
                execute(plan, subAgent, emitter, webServer, history);
                return;
            }
            System.out.print("补充要求> ");
            String req = reader.readLine();
            if (req == null || req.trim().isEmpty()) {
                System.out.println("[plan] 未收到补充要求，请重新选择。");
                continue;
            }
            replans++;
            System.out.println("[plan] 以“" + req.trim() + "”重新规划 (" + replans + "/" + MAX_REPLAN + ")。");
            extra = req.trim();
        }
    }

    private static void execute(ExecutionPlan plan, SubAgent subAgent, EventEmitter emitter,
                                WebServer webServer, List<Message> history) {
        if (webServer != null) {
            webServer.setCurrentPlan(plan);
        }
        System.out.println("[plan] 开始执行 " + plan.tasks().size() + " 个任务（可在 Web 观察 DAG 与事件流）…");
        new PlanExecutor(subAgent, emitter).execute(plan);
        appendPlanSummary(history, plan);
    }

    private static void printPlan(ExecutionPlan plan) {
        List<Task> order;
        try {
            order = plan.topoOrder();
        } catch (IllegalStateException e) {
            System.out.println("[plan] " + e.getMessage());
            return;
        }
        System.out.println("📋 计划（" + order.size() + " 个任务）：");
        for (Task t : order) {
            String deps = t.dependsOn().isEmpty() ? "-" : String.join(", ", t.dependsOn());
            System.out.printf("  %-4s %-22s 依赖: %s%n", t.id(), t.title(), deps);
        }
    }

    /** 把执行结果并入主对话 history，供后续任务/对话引用（审阅/取消阶段不写）。 */
    private static void appendPlanSummary(List<Message> history, ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder("计划执行结果：\n");
        for (Task t : plan.tasks()) {
            String c = t.conclusion();
            sb.append(" - ").append(t.id()).append(" ").append(t.title()).append(" ").append(t.status());
            if (c != null && !c.isBlank()) {
                sb.append(" — ").append(summarize(c));
            }
            sb.append('\n');
        }
        history.add(new Message("assistant", sb.toString().stripTrailing()));
    }

    /** Day 13：/trace list / show。 */
    private static void runTraceCommand(String input) {
        String arg = input.substring("/trace".length()).trim();
        try {
            if (arg.isEmpty() || "list".equals(arg)) {
                TraceRecorder.list();
                return;
            }
            if (arg.startsWith("show")) {
                String id = arg.substring("show".length()).trim();
                TraceRecorder.show(id);
                return;
            }
        } catch (IOException e) {
            System.out.println("[trace] 读取失败：" + e.getMessage());
            return;
        }
        System.out.println("[trace] 用法: /trace list | /trace show <id>");
    }

    /**
     * Day 14：/replay <id> 回放。
     * 默认 dry-run 只展示；带 --apply 时逐步 y/n 确认后真实执行（回放同样过工具护栏，不是特权通道）。
     */
    private static void runReplayFlow(BufferedReader reader, ToolRegistry registry, EventEmitter emitter,
                                     String input) throws IOException {
        String tail = input.substring("/replay".length()).trim();
        boolean apply = false;
        if (tail.endsWith("--apply")) {
            apply = true;
            tail = tail.substring(0, tail.length() - "--apply".length()).trim();
        }
        if (tail.isEmpty()) {
            System.out.println("[replay] 用法: /replay <id>（dry-run）| /replay <id> --apply（真实执行，逐步确认）");
            System.out.println("[replay] 用 /trace list 查看可回放的 id。");
            return;
        }
        Path file = TraceRecorder.traceDir().resolve(tail.endsWith(".jsonl") ? tail : tail + ".jsonl");
        if (!Files.isRegularFile(file)) {
            System.out.println("[replay] 未找到: " + tail);
            return;
        }
        TraceReplay replay;
        try {
            replay = TraceReplay.load(file);
        } catch (IOException e) {
            System.out.println("[replay] 读取失败: " + e.getMessage());
            return;
        }
        System.out.println("▶ 回放 trace " + tail + " (" + replay.metaLine() + ")");
        if (!apply) {
            dryRun(replay);
            return;
        }
        apply(reader, registry, emitter, replay);
    }

    /** 重演但绝不触碰工具：逐工具打印，末尾打 dry-run 标注。 */
    private static void dryRun(TraceReplay replay) {
        int idx = 0;
        for (TraceReplay.Event ev : replay.events()) {
            switch (ev.type()) {
                case "tool_call" -> {
                    idx++;
                    System.out.printf("  %d. tool_call %s %s%n",
                            idx, ev.str("tool"), TraceReplay.argsOf(ev));
                }
                case "turn_end" -> System.out.printf("  turn_end \"%s\"%n", simplify(ev.str("answer")));
                default -> { /* meta / llm / tool_result 等不参与 dry-run 时间线 */ }
            }
        }
        System.out.println("[dry-run] 未执行任何工具。加 --apply 真实执行（逐步确认）");
    }

    /** 逐步真实执行：每步 y/n（默认 n）；结果与原 trace 并排展示。事件也可视化到 Web 时间线。 */
    private static void apply(BufferedReader reader, ToolRegistry registry, EventEmitter emitter,
                              TraceReplay replay) throws IOException {
        if (registry == null) {
            System.out.println("[replay] --apply 需要已加载工具环境，当前不可用。");
            return;
        }
        TraceRecorder recording = null;
        try {
            recording = TraceRecorder.openReplay(replay.file().getFileName().toString(), "deepseek-chat", VERSION);
        } catch (IOException e) {
            System.out.println("[warn] replay 录制目录不可用，本次回放不落盘: " + e.getMessage());
        }
        int idx = 0;
        for (TraceReplay.Event ev : replay.events()) {
            if (!"tool_call".equals(ev.type())) {
                continue;
            }
            idx++;
            String tool = ev.str("tool");
            String args = TraceReplay.argsOf(ev);
            System.out.printf("  %d. tool_call %s %s%n", idx, tool, args);
            System.out.print("     执行? (y/N) > ");
            String line = reader.readLine();
            boolean yes = line != null && ("y".equalsIgnoreCase(line.trim()) || "yes".equalsIgnoreCase(line.trim()));
            if (!yes) {
                System.out.println("     已跳过 " + tool);
                continue;
            }
            traceCall(ev, tool, args, registry, emitter, recording);
        }
        if (recording != null) {
            recording.close();
            System.out.println("[replay] 本次回放已记为新 trace: " + recording.file().getFileName());
        }
        System.out.println("[replay] 回放结束。");
    }

    private static void traceCall(TraceReplay.Event ev, String tool, String args, ToolRegistry registry,
                                  EventEmitter emitter, TraceRecorder recording) {
        // 回放事件也广播给 Web/主会话，保持时间线一致
        Map<String, Object> callPayload = Map.of(
                "type", "tool_call", "tool", tool, "id", ev.str("id"),
                "args", args, "preview", simplify(args), "replay", true);
        emitter.emit("tool_call", callPayload);
        if (recording != null) {
            recording.emit("tool_call", callPayload);
        }
        String result;
        try {
            result = registry.execute(replayToolCall(ev, tool, args));
        } catch (Exception e) {
            result = "执行异常: " + e.getMessage();
        }
        String recorded = ev.str("result");
        Map<String, Object> resPayload = Map.of(
                "type", "tool_result", "tool", tool,
                "result", result, "preview", simplify(result), "replay", true);
        emitter.emit("tool_result", resPayload);
        if (recording != null) {
            recording.emit("tool_result", resPayload);
        }
        System.out.println("     本次结果: " + simplify(result));
        System.out.println("     录制结果: " + (recorded.isEmpty() ? "(无)" : simplify(recorded)));
    }

    private static com.agentcli.tool.ToolCall replayToolCall(TraceReplay.Event ev, String tool, String args) {
        return new com.agentcli.tool.ToolCall(ev.str("id"), tool, args);
    }

    /** 单行展示摘要（含工具结果，避免把整个文件内容刷屏）。 */
    private static String simplify(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replace('\n', ' ').trim();
        return one.length() <= 100 ? one : one.substring(0, 100) + "…";
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
        tips.put("/plan", "生成任务计划 → 审阅后执行（r/i/c）");
        tips.put("/trace", "查看录制：list / show <id>");
        tips.put("/replay", "回放 trace：/replay <id>（dry-run）；--apply 真实执行");
        tips.forEach((cmd, desc) -> System.out.printf("  %-10s · %s%n", cmd, desc));
        System.out.println();
    }
}
