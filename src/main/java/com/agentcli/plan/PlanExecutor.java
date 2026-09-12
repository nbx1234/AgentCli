package com.agentcli.plan;

import com.agentcli.web.EventEmitter;
import com.agentcli.web.EventPayload;

import java.util.List;

/**
 * 按拓扑序（就绪优先）驱动计划执行，推进任务状态机并广播过程事件。
 *
 * 执行模型：每轮取依赖全 DONE 的就绪任务逐个执行（串行）；失败/被跳过的前置任务
 * 会使下游级联标 SKIPPED。串行即可，并行留到打磨期。
 */
public final class PlanExecutor {

    private final TaskRunner runner;
    private final EventEmitter events;

    public PlanExecutor(TaskRunner runner, EventEmitter events) {
        this.runner = runner;
        this.events = events;
    }

    public void execute(ExecutionPlan plan) {
        while (!plan.settled()) {
            List<Task> ready = plan.readyTasks();
            if (ready.isEmpty()) {
                markSkips(plan); // 剩余 PENDING 都被失败前置阻塞 → 全标 SKIPPED
                break;
            }
            for (Task t : ready) {
                runOne(t, plan);
            }
            markSkips(plan);
        }
    }

    private void runOne(Task t, ExecutionPlan plan) {
        t.setStatus(Task.Status.RUNNING);
        events.emit("task_start", EventPayload.create("task_start")
                .put("task", t.id()).put("title", t.title())
                .put("deps", t.dependsOn()).build());
        try {
            String conclusion = runner.run(t, plan);
            if (conclusion != null && conclusion.startsWith("[FAILED]")) {
                fail(t, plan);
            } else {
                t.setConclusion(conclusion);
                t.setStatus(Task.Status.DONE);
                events.emit("task_end", EventPayload.create("task_end")
                        .put("task", t.id()).put("status", Task.Status.DONE.name())
                        .put("preview", preview(conclusion)).build());
            }
        } catch (Exception e) {
            fail(t, plan, e.getMessage());
        }
    }

    private void fail(Task t, ExecutionPlan plan) {
        t.setStatus(Task.Status.FAILED);
        events.emit("task_end", EventPayload.create("task_end")
                .put("task", t.id()).put("status", Task.Status.FAILED.name())
                .put("preview", "[FAILED]").build());
    }

    private void fail(Task t, ExecutionPlan plan, String error) {
        t.setStatus(Task.Status.FAILED);
        events.emit("task_end", EventPayload.create("task_end")
                .put("task", t.id()).put("status", Task.Status.FAILED.name())
                .put("preview", error).build());
    }

    private void markSkips(ExecutionPlan plan) {
        for (Task t : plan.tasks()) {
            if (t.status() == Task.Status.PENDING && plan.hasBlockedDep(t)) {
                t.setStatus(Task.Status.SKIPPED);
                events.emit("task_end", EventPayload.create("task_end")
                        .put("task", t.id()).put("status", Task.Status.SKIPPED.name())
                        .put("preview", "前置任务失败，跳过").build());
            }
        }
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        String line = s.lines().findFirst().orElse("");
        return line.length() <= 100 ? line : line.substring(0, 100) + "…";
    }
}