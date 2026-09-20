package com.agentcli;

import com.agentcli.agent.Agent;
import com.agentcli.agent.SubAgent;
import com.agentcli.context.HistoryCompactor;
import com.agentcli.context.TokenEstimator;
import com.agentcli.hitl.Approver;
import com.agentcli.hitl.ApprovalPolicy;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.DeepSeekClient;
import com.agentcli.llm.Message;
import com.agentcli.mcp.McpServerManager;
import com.agentcli.mcp.MentionExpander;
import com.agentcli.plan.ExecutionPlan;
import com.agentcli.plan.PlanExecutor;
import com.agentcli.plan.PlanReviewParser;
import com.agentcli.plan.Planner;
import com.agentcli.plan.Task;
import com.agentcli.policy.AuditLog;
import com.agentcli.skill.SkillRegistry;
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
        McpServerManager[] mcpRef = new McpServerManager[1];
        // Day 18：审批面板与 plan 审阅共用同一条 stdin，reader 提前创建
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        // Day 19：Token 估算 / 上下文压缩（agent 与 /ctx /compact 共用同一实例）
        TokenEstimator estimator = new TokenEstimator();
        HistoryCompactor[] compactorRef = new HistoryCompactor[1];
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
            // Day 19：plan 并行任务会并发 emit → 聚合 emitter 加锁，保证 console/web/trace 不交错
            final Object emitLock = new Object();
            EventEmitter emitter = (t, p) -> {
                synchronized (emitLock) {
                    sink.emit(t, p);
                    TraceRecorder tr = traceRef[0];
                    if (tr != null) {
                        tr.emit(t, p);
                    }
                }
            };
            registry = buildRegistry();
            emitterRef[0] = emitter;
            // Day 18：HITL 审批层（只读放行 / 写执行必审 / 黑名单 DENY），审计日志可降级
            AuditLog audit = null;
            try {
                audit = AuditLog.openDefault();
            } catch (IOException ae) {
                System.out.println("[warn] 审计日志不可用: " + ae.getMessage());
            }
            Approver approver = new Approver(new ApprovalPolicy(), reader::readLine, audit, emitter);
            // Day 19：构造共享压缩器，传给 Agent 实现自动压缩；/compact 手动触发
            HistoryCompactor compactor = new HistoryCompactor(llm);
            compactorRef[0] = compactor;
            long window = contextWindow();
            agent = new Agent(llm, registry, emitter, approver, compactor, window);
            subAgent = new SubAgent(llm, registry, emitter, approver, compactor, window);
        } catch (IllegalStateException e) {
            System.out.println("[warn] " + e.getMessage());
            System.out.println("      请复制 .env.example 为 .env 并填入 DEEPSEEK_API_KEY 后再试。");
        }
        // Trace 目录不可用等异常到此已单独降级，主流程仍可继续。

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
            if (mcpRef[0] != null) {
                mcpRef[0].closeAll();
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
            // Day 18：/audit 查看审计日志（最近 20 条，不依赖 LLM）
            if ("/audit".equals(input)) {
                runAuditCommand();
                continue;
            }
            // Day 19：/ctx 预算占用；/compact 手动立即压缩
            if ("/ctx".equals(input)) {
                runCtxCommand(estimator, history);
                continue;
            }
            if ("/compact".equals(input)) {
                if (compactorRef[0] == null || llm == null) {
                    System.out.println("[compact] 需要 LLM，请先配置 .env。");
                } else {
                    runCompactCommand(compactorRef[0], estimator, history);
                }
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
            // Day 15：/skill 录制即技能：save / list / show / run
            if (input.startsWith("/skill")) {
                runSkillFlow(reader, llm, agent, emitterRef[0], history, input);
                continue;
            }
            // Day 16：/mcp 连接外部 MCP server，工具动态注册进 ToolRegistry
            if (input.startsWith("/mcp")) {
                runMcpCommand(registry, mcpRef, input);
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
            // Day 17：@server:uri 提及展开（在 history 追加之前）；trace 的 user 字段记原始输入
            String promptInput = input;
            McpServerManager mcp = mcpRef[0];
            if (mcp != null && !mcp.connections().isEmpty()) {
                String expanded = MentionExpander.expand(input, mcp::resolveResource);
                if (!expanded.equals(input)) {
                    emitterRef[0].emit("mention_expanded",
                            Map.of("type", "mention_expanded", "input", input,
                                    "preview", simplify(expanded)));
                    promptInput = expanded;
                    System.out.println("[mention] 已展开 " + MentionExpander.findMentions(input).size() + " 个资源引用");
                }
            }
            // Day 5：交给 Agent 走 ReAct 循环（内部处理历史、工具调用与答案）
            try {
                String reply = agent.run(promptInput, history);
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

    /** Day 18：/audit 展示审计日志最近 20 条。 */
    private static void runAuditCommand() {
        try {
            List<Map<String, Object>> rows = AuditLog.recent(20);
            if (rows.isEmpty()) {
                System.out.println("[audit] 暂无审计记录");
                return;
            }
            System.out.println("[audit] 最近 " + rows.size() + " 条（" + AuditLog.defaultFile() + "）:");
            for (Map<String, Object> r : rows) {
                System.out.printf("  %-6s %-20s %-8s %s%n",
                        r.get("decision"), r.get("tool"), r.get("source"),
                        simplify(String.valueOf(r.get("args"))));
            }
        } catch (IOException e) {
            System.out.println("[audit] 读取失败: " + e.getMessage());
        }
    }

    /** Day 19：/ctx 展示 token 占用 / 窗口百分比 / 距离阈值。 */
    private static void runCtxCommand(TokenEstimator estimator, List<Message> history) {
        long window = contextWindow();
        long threshold = window - 8 * 1024L - 6 * 1024L;
        int used = estimator.estimate(history);
        int msgs = history.size();
        long pct = window <= 0 ? 0 : used * 100 / window;
        System.out.println("[ctx] token 占用 " + used + " / " + window + " (" + pct + "%) · 消息 " + msgs + " 条");
        System.out.println("      压缩阈值 " + threshold + "，距离 " + Math.max(0, threshold - used) + " tokens");
    }

    /** Day 19：/compact 手动立即压缩（同步；输出前后对比）。 */
    private static void runCompactCommand(HistoryCompactor compactor, TokenEstimator estimator,
                                          List<Message> history) {
        if (history.isEmpty()) {
            System.out.println("[compact] 历史为空，无需压缩。");
            return;
        }
        int before = estimator.estimate(history);
        List<Message> compacted = compactor.compact(history);
        history.clear();
        history.addAll(compacted);
        int after = estimator.estimate(history);
        System.out.println("[compact] " + before + " → " + after + " tokens（" + (before - after) + " 节省）");
    }

    /** Day 19：上下文窗口大小（token）：AGENTCLI_CONTEXT_WINDOW 覆盖，默认 64k。 */
    private static long contextWindow() {
        String v = Env.get("AGENTCLI_CONTEXT_WINDOW");
        if (v != null && !v.isBlank()) {
            try {
                long w = Long.parseLong(v.trim());
                if (w > 0) {
                    return w;
                }
            } catch (NumberFormatException ignored) {
                // 回落默认
            }
        }
        return 65536;
    }

    /** 组装工具注册表（读写文件 + 执行命令），走路径/命令护栏；写入审批由 Approver 负责。 */
    private static ToolRegistry buildRegistry() throws IOException {
        File root = Env.rootPath().toFile();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(root));
        registry.register(new WriteFileTool(root));
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

    // ---- Day 15：/skill 录制即技能 ----

    /**
     * /skill save <name> <traceId>：LLM 分析 trace 产出参数化模板，用户确认后落盘。
     * /skill list / show <name> / run <name> k=v ...：run 渲染后作为 user prompt 走普通 Agent。
     */
    private static void runSkillFlow(BufferedReader reader, ChatClient llm, Agent agent,
                                     EventEmitter emitter, List<Message> history, String input) throws IOException {
        String tail = input.substring("/skill".length()).trim();
        if (tail.isEmpty()) {
            System.out.println("[skill] 用法: save <name> <traceId> | list | show <name> | run <name> k=v ...");
            return;
        }
        String[] parts = tail.split("\\s+", 3);
        String sub = parts[0];
        try {
            switch (sub) {
                case "list" -> listSkills();
                case "show" -> showSkill(parts.length > 1 ? parts[1] : "");
                case "save" -> saveSkill(reader, llm, parts.length > 1 ? parts[1] : "",
                        parts.length > 2 ? parts[2] : "");
                case "run" -> runSkill(reader, llm, agent, emitter, history, parts.length > 1 ? parts[1] : "",
                        parts.length > 2 ? parts[2] : "");
                default -> System.out.println("[skill] 未知子命令: " + sub
                        + "（save | list | show | run）");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("[skill] " + e.getMessage());
        }
    }

    private static void listSkills() throws IOException {
        SkillRegistry reg = new SkillRegistry();
        java.util.List<SkillRegistry.Skill> all = reg.list();
        if (all.isEmpty()) {
            System.out.println("[skill] 还没有技能。用 /skill save <name> <traceId> 从一条 trace 录制。");
            return;
        }
        System.out.println("[skill] " + all.size() + " 个技能:");
        for (SkillRegistry.Skill s : all) {
            System.out.printf("  %-20s 目标: %s%n", s.name, s.goal);
        }
    }

    private static void showSkill(String name) throws IOException {
        if (name.isEmpty()) {
            System.out.println("[skill] 用法: /skill show <name>");
            return;
        }
        SkillRegistry reg = new SkillRegistry();
        SkillRegistry.Skill s = reg.load(name);
        System.out.println("技能 " + s.name + " · 目标: " + s.goal);
        System.out.println("参数: " + String.join(", ", s.params));
        System.out.println(s.body);
    }

    /** save：LLM 非流式生成模板 → 展示 → y/n 确认 → 落盘。 */
    private static void saveSkill(BufferedReader reader, ChatClient llm, String name, String traceId)
            throws IOException {
        if (name.isEmpty() || traceId.isEmpty()) {
            System.out.println("[skill] 用法: /skill save <name> <traceId>");
            return;
        }
        if (llm == null) {
            System.out.println("[skill] 需要 LLM 才能分析 trace，先配置 .env。");
            return;
        }
        Path file = TraceRecorder.traceDir().resolve(traceId.endsWith(".jsonl") ? traceId : traceId + ".jsonl");
        if (!Files.isRegularFile(file)) {
            System.out.println("[skill] 未找到 trace: " + traceId);
            return;
        }
        TraceReplay replay;
        try {
            replay = TraceReplay.load(file);
        } catch (IOException e) {
            System.out.println("[skill] trace 读取失败: " + e.getMessage());
            return;
        }
        // 把 trace 压缩成步骤摘要喂给 LLM
        StringBuilder steps = new StringBuilder();
        for (TraceReplay.Event ev : replay.events()) {
            switch (ev.type()) {
                case "tool_call" -> steps.append("- ").append(ev.str("tool"))
                        .append(' ').append(TraceReplay.argsOf(ev)).append('\n');
                case "turn_end" -> steps.append("- answer: ").append(simplify(ev.str("answer"))).append('\n');
                default -> { }
            }
        }
        String prompt = "根据下面这段 Agent 执行记录，抽取出一个可复用的技能模板。"
                + "只参数化\"输入类\"值（文件路径/URL/目录等），步骤逻辑保持原样。\n"
                + "输出严格 Markdown 格式（不要输出其他文字）：\n"
                + "---\nname: " + name + "\ngoal: 一句话目标\nparams: [参数1, 参数2]\n"
                + "---\n## 步骤\n1. ...\n\n执行记录：\n" + steps;
        System.out.println("[skill] LLM 正在分析 trace 并生成模板…");
        String template;
        try {
            template = llm.call(java.util.List.of(new Message("user", prompt)));
        } catch (Exception e) {
            System.out.println("[skill] 模板生成失败: " + e.getMessage());
            return;
        }
        System.out.println("--- 生成的技能模板 ---");
        System.out.println(template);
        System.out.print("保存为技能 " + name + " ？(y/N) > ");
        String line = reader.readLine();
        if (line == null || !("y".equalsIgnoreCase(line.trim()) || "yes".equalsIgnoreCase(line.trim()))) {
            System.out.println("[skill] 已取消保存。");
            return;
        }
        try {
            SkillRegistry.Skill skill = SkillRegistry.parse(template);
            SkillRegistry reg = new SkillRegistry();
            Path saved = reg.save(skill);
            System.out.println("[skill] 已保存: " + saved);
        } catch (IllegalArgumentException e) {
            System.out.println("[skill] 模板解析失败: " + e.getMessage());
        }
    }

    /** run：k=v 替换模板变量 → 渲染结果作为 user prompt 走普通 Agent（同样录 trace，meta 标 skill）。 */
    private static void runSkill(BufferedReader reader, ChatClient llm, Agent agent, EventEmitter emitter,
                                 List<Message> history, String name, String kv) throws IOException {
        if (name.isEmpty()) {
            System.out.println("[skill] 用法: /skill run <name> k1=v1 k2=v2 ...");
            return;
        }
        if (llm == null || agent == null) {
            System.out.println("[skill] 需要 LLM 才能执行技能。");
            return;
        }
        SkillRegistry reg = new SkillRegistry();
        SkillRegistry.Skill skill = reg.load(name);
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String item : kv.split("\\s+")) {
            if (item.isBlank()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0) {
                System.out.println("[skill] 参数格式应为 k=v，收到: " + item);
                return;
            }
            values.put(item.substring(0, eq), item.substring(eq + 1));
        }
        String prompt;
        try {
            prompt = reg.render(skill, values);
        } catch (IllegalArgumentException e) {
            System.out.println("[skill] " + e.getMessage());
            return;
        }
        System.out.println("[skill] 执行技能 " + skill.name + "：" + simplify(prompt));
        // 技能执行轮次录成独立 trace（type=skill, skill=name），随事件流广播
        TraceRecorder skillTrace = null;
        try {
            skillTrace = TraceRecorder.openSkill(skill.name, "deepseek-chat", VERSION);
        } catch (IOException e) {
            System.out.println("[warn] 技能 trace 不可用: " + e.getMessage());
        }
        TraceRecorder st = skillTrace;
        try {
            String reply = agent.run(prompt, history);
            System.out.println(reply);
        } catch (Exception e) {
            System.out.println("[error] " + e.getMessage());
        } finally {
            if (st != null) {
                try {
                    st.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ---- Day 16：/mcp ----

    /**
     * /mcp 读取 ~/.agentcli/mcp.json 并连接所有 server，
     * 把每个工具以 mcp__<server>__<tool> 注册进 ToolRegistry，然后打印状态。
     */
    private static void runMcpCommand(ToolRegistry registry, McpServerManager[] mcpRef, String input) {
        if (registry == null) {
            System.out.println("[mcp] 工具环境不可用。");
            return;
        }
        String tail = input.substring("/mcp".length()).trim();
        if (!tail.isEmpty() && !"connect".equals(tail) && !"status".equals(tail)) {
            System.out.println("[mcp] 用法: /mcp connect | /mcp status");
            return;
        }
        McpServerManager manager = mcpRef[0] != null ? mcpRef[0] : new McpServerManager();
        mcpRef[0] = manager;
        if ("status".equals(tail) && !manager.connections().isEmpty()) {
            printMcpStatus(manager);
            return;
        }
        try {
            manager.connectAll(registry);
            printMcpStatus(manager);
        } catch (IOException e) {
            System.out.println("[mcp] " + e.getMessage());
        }
    }

    private static void printMcpStatus(McpServerManager manager) {
        if (manager.connections().isEmpty()) {
            System.out.println("[mcp] 未连接任何 server（配置: " + manager.configPath() + "）");
            return;
        }
        System.out.println("[mcp] 已连接 " + manager.connections().size() + " 个 server:");
        for (McpServerManager.Connection c : manager.connections()) {
            System.out.printf("  %-16s alive=%s tools=%d%n", c.server, c.client.isAlive(), c.toolCount);
        }
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
        tips.put("/audit", "查看审计日志（最近 20 条）");
        tips.put("/ctx", "查看 token 占用与压缩阈值");
        tips.put("/compact", "手动立即压缩上下文");
        tips.put("/plan", "生成任务计划 → 审阅后执行（r/i/c）");
        tips.put("/trace", "查看录制：list / show <id>");
        tips.put("/replay", "回放 trace：/replay <id>（dry-run）；--apply 真实执行");
        tips.put("/skill", "录制即技能：save/list/show/run");
        tips.put("/mcp", "连接 MCP server 并列出工具状态");
        tips.forEach((cmd, desc) -> System.out.printf("  %-10s · %s%n", cmd, desc));
        System.out.println();
    }
}
