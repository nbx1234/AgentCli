package com.agentcli.hitl;

/**
 * Day 18：一次待审批的工具调用描述。
 *
 * @param tool            工具名
 * @param argsSummary     参数摘要（execute_command 显示完整命令）
 * @param riskDescription 一句话风险说明，展示在审批面板
 */
public record ApprovalRequest(String tool, String argsSummary, String riskDescription) {
}
