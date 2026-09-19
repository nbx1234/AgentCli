package com.agentcli.hitl;

import com.agentcli.policy.CommandGuard;
import com.agentcli.tool.ToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Day 18：HITL 审批策略。
 *
 * 把工具调用分成三类：只读放行（ALLOW）、写/执行必审（ASK）、危险命令直接拒绝（DENY）。
 * mcp 只读判定用<b>名称启发式</b>（list/get/read/search 开头）——这是启发式，不是保证。
 *
 * 策略矩阵：
 * | 工具 | 决策 |
 * |---|---|
 * | read_file | ALLOW |
 * | write_file | ASK |
 * | execute_command | ASK + CommandGuard 可 DENY |
 * | mcp 只读（list/read 类名） | ALLOW |
 * | 其他 mcp | ASK |
 * | CommandGuard 黑名单 | DENY（用户确认也无效） |
 */
public final class ApprovalPolicy {

    public enum Decision { ALLOW, ASK, DENY }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 判定一次工具调用该走哪条路。 */
    public Decision decide(ToolCall tc) {
        String tool = tc.name();
        if (tool.startsWith("mcp__")) {
            return isReadOnlyMcp(tool) ? Decision.ALLOW : Decision.ASK;
        }
        switch (tool) {
            case "read_file":
                return Decision.ALLOW;
            case "execute_command":
                try {
                    CommandGuard.check(commandOf(tc));
                    return Decision.ASK;
                } catch (SecurityException e) {
                    return Decision.DENY;
                }
            default:
                // write_file / 未知工具 一律 ASK，宁可多问
                return Decision.ASK;
        }
    }

    /** mcp 工具名形如 mcp__<server>__<tool>；只读启发式看末段是否以 list/get/read/search 开头。 */
    static boolean isReadOnlyMcp(String tool) {
        int last = tool.lastIndexOf("__");
        String name = last >= 0 ? tool.substring(last + 2) : tool;
        return name.startsWith("list") || name.startsWith("get")
                || name.startsWith("read") || name.startsWith("search");
    }

    private static String commandOf(ToolCall tc) {
        try {
            Map<String, Object> args = (tc.argumentsJson() == null || tc.argumentsJson().isBlank())
                    ? Map.of()
                    : MAPPER.readValue(tc.argumentsJson(), new TypeReference<Map<String, Object>>() {});
            Object c = args.get("command");
            return c == null ? "" : c.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
