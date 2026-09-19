package com.agentcli.hitl;

import com.agentcli.agent.Agent;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.LlmResponse;
import com.agentcli.llm.Message;
import com.agentcli.policy.AuditLog;
import com.agentcli.tool.ToolCall;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import com.agentcli.web.EventEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day 18：HITL 审批策略测试。
 * 覆盖策略矩阵、DENY 后 execute 不被调用、审计落盘、会话内 a 记忆。
 */
class ApprovalPolicyTest {

    private final ApprovalPolicy policy = new ApprovalPolicy();

    @TempDir
    Path tmp;

    // ---- 策略矩阵 ----

    @Test
    void readFileIsAllowed() {
        assertEquals(ApprovalPolicy.Decision.ALLOW,
                policy.decide(new ToolCall("1", "read_file", "{\"path\":\"README.md\"}")));
    }

    @Test
    void writeFileAsks() {
        assertEquals(ApprovalPolicy.Decision.ASK,
                policy.decide(new ToolCall("2", "write_file", "{\"path\":\"a.txt\",\"content\":\"x\"}")));
    }

    @Test
    void safeCommandAsks() {
        assertEquals(ApprovalPolicy.Decision.ASK,
                policy.decide(new ToolCall("3", "execute_command", "{\"command\":\"ls -la\"}")));
    }

    @Test
    void dangerousCommandDenied() {
        assertEquals(ApprovalPolicy.Decision.DENY,
                policy.decide(new ToolCall("4", "execute_command", "{\"command\":\"rm -rf /\"}")));
        assertEquals(ApprovalPolicy.Decision.DENY,
                policy.decide(new ToolCall("5", "execute_command", "{\"command\":\"sudo echo hi\"}")));
    }

    @Test
    void readOnlyMcpAllowed() {
        assertEquals(ApprovalPolicy.Decision.ALLOW,
                policy.decide(new ToolCall("6", "mcp__fs__read_file", "{}")));
        assertEquals(ApprovalPolicy.Decision.ALLOW,
                policy.decide(new ToolCall("7", "mcp__fs__list_directory", "{}")));
        assertEquals(ApprovalPolicy.Decision.ALLOW,
                policy.decide(new ToolCall("8", "mcp__fs__search_files", "{}")));
    }

    @Test
    void writeMcpAsks() {
        assertEquals(ApprovalPolicy.Decision.ASK,
                policy.decide(new ToolCall("9", "mcp__fs__write_file", "{}")));
        assertEquals(ApprovalPolicy.Decision.ASK,
                policy.decide(new ToolCall("10", "mcp__fs__delete_file", "{}")));
    }

    // ---- Approver 行为 ----

    @Test
    void allowInterceptsToNull() throws Exception {
        Approver approver = new Approver(policy, () -> "y", null, EventEmitter.NOOP);
        assertNull(approver.intercept(new ToolCall("11", "read_file", "{\"path\":\"README.md\"}")));
    }

    @Test
    void userDenyReturnsRefusal() throws Exception {
        Approver approver = new Approver(policy, () -> "n", null, EventEmitter.NOOP);
        String r = approver.intercept(new ToolCall("12", "write_file", "{\"path\":\"a\",\"content\":\"x\"}"));
        assertNotNull(r);
        assertTrue(r.contains("拒绝"));
    }

    @Test
    void denySkipsPrompt() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        Approver approver = new Approver(policy, () -> { reads.incrementAndGet(); return "y"; }, null, EventEmitter.NOOP);
        assertNotNull(approver.intercept(new ToolCall("13", "execute_command", "{\"command\":\"rm -rf /\"}")));
        assertEquals(0, reads.get(), "DENY 不应弹确认");
    }

    @Test
    void sessionAllowSkipsSecondAsk() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        Approver approver = new Approver(policy,
                () -> { reads.incrementAndGet(); return "a"; }, null, EventEmitter.NOOP);
        ToolCall tc = new ToolCall("14", "write_file", "{\"path\":\"a\",\"content\":\"x\"}");
        assertNull(approver.intercept(tc));
        assertNull(approver.intercept(tc));
        assertEquals(1, reads.get(), "会话内 a 后同类工具不再打断");
    }

    /** DENY 后 Agent 不会调用 registry.execute（拒绝文本作为 tool 结果回传）。 */
    @Test
    void deniedCommandIsNotExecuted() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ToolDefinition("execute_command", "d", "{}"),
                args -> { executed.set(true); return "ran"; });

        Approver approver = new Approver(policy, () -> "y", null, EventEmitter.NOOP);
        Agent agent = new Agent(stubClient(), reg, EventEmitter.NOOP, approver);

        String answer = agent.run("do it", new ArrayList<>());
        assertEquals("done", answer);
        assertFalse(executed.get(), "DENY 后 execute 不应被调用");
    }

    // ---- 审计日志 ----

    @Test
    void approverRecordsAudit() throws Exception {
        Path file = tmp.resolve("audit.jsonl");
        try (AuditLog audit = new AuditLog(file)) {
            Approver approver = new Approver(policy, () -> "y", audit, EventEmitter.NOOP);
            assertNull(approver.intercept(new ToolCall("15", "write_file", "{\"path\":\"a\",\"content\":\"x\"}")));
            assertNotNull(approver.intercept(new ToolCall("16", "execute_command", "{\"command\":\"rm -rf /\"}")));
        }
        List<Map<String, Object>> rows = AuditLog.read(file, 20);
        assertEquals(2, rows.size());
        assertEquals("write_file", rows.get(0).get("tool"));
        assertEquals("ALLOW", rows.get(0).get("decision"));
        assertEquals("user", rows.get(0).get("source"));
        assertEquals("execute_command", rows.get(1).get("tool"));
        assertEquals("DENY", rows.get(1).get("decision"));
        assertEquals("policy", rows.get(1).get("source"));
    }

    @Test
    void auditReadNonexistentReturnsEmpty() throws Exception {
        assertTrue(AuditLog.read(tmp.resolve("missing.jsonl"), 20).isEmpty());
    }

    /** 首轮返回一条 DENY 命令，次轮返回纯文本收尾。 */
    private static ChatClient stubClient() {
        AtomicInteger calls = new AtomicInteger();
        return new ChatClient() {
            @Override
            public String call(List<Message> messages) {
                return "";
            }

            @Override
            public LlmResponse call(List<Message> messages, List<ToolDefinition> tools) {
                if (calls.getAndIncrement() == 0) {
                    return new LlmResponse("", List.of(
                            new ToolCall("1", "execute_command", "{\"command\":\"rm -rf /\"}")));
                }
                return new LlmResponse("done", List.of());
            }

            @Override
            public void callStream(List<Message> messages, Consumer<String> onDelta) {
            }
        };
    }
}
