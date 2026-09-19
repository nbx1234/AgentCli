package com.agentcli.hitl;

import com.agentcli.policy.AuditLog;
import com.agentcli.tool.ToolCall;
import com.agentcli.web.EventEmitter;
import com.agentcli.web.EventPayload;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Day 18：HITL 审批器（控制台实现）。
 *
 * 按 {@link ApprovalPolicy} 分派：
 * <ul>
 *   <li>ALLOW：直接放行，只记审计</li>
 *   <li>DENY：直接拒绝（无确认弹窗，用户确认也无效）</li>
 *   <li>ASK：弹出面板 <code>[y]允许 [n]拒绝(默认) [a]本会话允许该工具</code></li>
 * </ul>
 * 每次决策写审计日志并广播 approval_request / approval_result 事件到 Web 时间线。
 * 拒绝时返回拒绝文本，由 Agent 作为 tool 结果回传给 LLM 调整方案。
 */
public final class Approver {

    /** 读取一行用户输入（Main 用 System.in 包装的 reader 实现）。 */
    @FunctionalInterface
    public interface LineReader {
        String readLine() throws IOException;
    }

    private final ApprovalPolicy policy;
    private final LineReader reader;
    private final AuditLog audit;
    private final EventEmitter events;
    /** 本会话已放行（选了 a）的工具名。 */
    private final Set<String> sessionAllowed = new HashSet<>();

    public Approver(ApprovalPolicy policy, LineReader reader, AuditLog audit, EventEmitter events) {
        this.policy = policy;
        this.reader = reader;
        this.audit = audit;
        this.events = events;
    }

    /**
     * 在工具执行前调用。
     *
     * @return null = 放行；否则为拒绝文本（作为 tool 结果回传）
     */
    public String intercept(ToolCall tc) throws IOException {
        ApprovalPolicy.Decision d = policy.decide(tc);
        String args = tc.argumentsJson();
        if (d == ApprovalPolicy.Decision.ALLOW) {
            audit("ALLOW", "policy", tc, args);
            return null;
        }
        if (d == ApprovalPolicy.Decision.DENY) {
            String risk = "命中安全黑名单，直接拒绝（用户确认也无效）";
            events.emit("approval_request", EventPayload.create("approval_request")
                    .put("tool", tc.name()).put("args", args).put("risk", risk).build());
            events.emit("approval_result", EventPayload.create("approval_result")
                    .put("tool", tc.name()).put("decision", "DENY").put("source", "policy").build());
            audit("DENY", "policy", tc, args);
            return "已拒绝执行 " + tc.name() + "：" + risk;
        }
        // ASK
        if (sessionAllowed.contains(tc.name())) {
            audit("ALLOW", "session", tc, args);
            return null;
        }
        ApprovalRequest req = describe(tc);
        events.emit("approval_request", EventPayload.create("approval_request")
                .put("tool", tc.name()).put("args", req.argsSummary()).put("risk", req.riskDescription()).build());
        System.out.println("⚠ 需要确认: " + tc.name());
        System.out.println("   " + req.riskDescription());
        System.out.print("   [y]允许  [n]拒绝(默认)  [a]本会话允许 " + tc.name() + "  > ");
        String line = reader.readLine();
        String in = line == null ? "" : line.trim().toLowerCase();
        if ("y".equals(in) || "yes".equals(in)) {
            audit("ALLOW", "user", tc, args);
            emitResult(tc, "ALLOW", "user");
            return null;
        }
        if ("a".equals(in) || "allow".equals(in)) {
            sessionAllowed.add(tc.name());
            audit("ALLOW", "session", tc, args);
            emitResult(tc, "ALLOW", "session");
            return null;
        }
        audit("DENY", "user", tc, args);
        emitResult(tc, "DENY", "user");
        return "用户拒绝了 " + tc.name() + "（" + req.riskDescription() + "），请调整方案";
    }

    private ApprovalRequest describe(ToolCall tc) {
        String tool = tc.name();
        if ("execute_command".equals(tool)) {
            return new ApprovalRequest(tool, tc.argumentsJson(), "执行命令，可能修改系统状态");
        }
        if ("write_file".equals(tool)) {
            return new ApprovalRequest(tool, tc.argumentsJson(), "写文件，可能覆盖现有内容");
        }
        return new ApprovalRequest(tool, tc.argumentsJson(), "调用外部工具");
    }

    private void emitResult(ToolCall tc, String decision, String source) {
        events.emit("approval_result", EventPayload.create("approval_result")
                .put("tool", tc.name()).put("decision", decision).put("source", source).build());
    }

    private void audit(String decision, String source, ToolCall tc, String args) {
        if (audit != null) {
            audit.record(tc.name(), args, decision, source);
        }
    }
}
