package com.agentcli.agent;

import com.agentcli.context.HistoryCompactor;
import com.agentcli.hitl.Approver;
import com.agentcli.llm.ChatClient;
import com.agentcli.llm.Message;
import com.agentcli.plan.ExecutionPlan;
import com.agentcli.plan.Task;
import com.agentcli.plan.TaskRunner;
import com.agentcli.tool.ToolRegistry;
import com.agentcli.web.EventEmitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 每个任务独立的子执行体：复用 Agent 的 ReAct 循环与工具注册表，
 * 但持有一个全新的对话历史，不会污染主会话。
 *
 * 工具调用结果只在子历史里流转，最终只回传"结论摘要"给上层。
 */
public final class SubAgent implements TaskRunner {

    private final Agent agent;

    public SubAgent(ChatClient client, ToolRegistry registry, EventEmitter events) {
        this(client, registry, events, null);
    }

    /** Day 18：计划任务内的工具调用同样过审批层，不绕过安全线。 */
    public SubAgent(ChatClient client, ToolRegistry registry, EventEmitter events, Approver approver) {
        this(client, registry, events, approver, null, 0);
    }

    /** Day 19：带上下文压缩的计划子执行体（长任务同样不爆窗）。 */
    public SubAgent(ChatClient client, ToolRegistry registry, EventEmitter events, Approver approver,
                    HistoryCompactor compactor, long windowTokens) {
        this.agent = new Agent(client, registry, events, approver, compactor, windowTokens);
    }

    @Override
    public String run(Task task, ExecutionPlan plan) throws Exception {
        List<Message> subHistory = new ArrayList<>();
        String input = task.prompt() + depsContext(plan, task);
        return agent.run(input, subHistory);
    }

    /** 把已完成前置任务的结论摘要拼进任务指令，让 t2 能引用 t1 的产出。 */
    private String depsContext(ExecutionPlan plan, Task task) {
        StringBuilder sb = new StringBuilder();
        for (String depId : task.dependsOn()) {
            Task dep = plan.byId(depId);
            if (dep != null && dep.status() == Task.Status.DONE && dep.conclusion() != null
                    && !dep.conclusion().isBlank()) {
                sb.append("\n\n前置任务[").append(depId).append("] ").append(dep.title()).append(" 的结论：\n")
                        .append(dep.conclusion());
            }
        }
        return sb.toString();
    }
}