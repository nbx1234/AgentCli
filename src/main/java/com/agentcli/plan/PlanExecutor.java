package com.agentcli.plan;

import com.agentcli.web.EventEmitter;
import com.agentcli.web.EventPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 按拓扑序（就绪优先）驱动计划执行，推进任务状态机并广播过程事件。
 *
 * Day 19：同一批内互无依赖的 ready 任务用固定线程池（4）并行执行，结果按原序收集；
 * 事件发射经 {@code lock} 串行化，保证 SSE / trace 不交错。
 */
public final class PlanExecutor {

    private static final int PARALLEL = 4;
    /** 串行化事件发射的锁：并发任务都在这个锁上广播，避免时间线/trace 交错。 */
    private static final Object EVENT_LOCK = new Object();

    private final TaskRunner runner;
    private final EventEmitter events;

    public PlanExecutor(TaskRunner runner, EventEmitter events) {
        this.runner = runner;
        this.events = events;
    }

    public void execute(ExecutionPlan plan) {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            while (!plan.settled()) {
                List<Task> ready = plan.readyTasks();
                if (ready.isEmpty()) {
                    markSkips(plan); // 剩余 PENDING 都被失败前置阻塞 → 全标 SKIPPED
                    break;
                }
                List<Future<?>> futures = new ArrayList<>();
                for (Task t : ready) {
                    futures.add(pool.submit(() -> runOne(t, plan)));
                }
                for (Future<?> f : futures) {
                    try {
                        f.get();
                    } catch (Exception ignored) {
                        // 任务自身已把 FAILED 写进状态，这里只需消费异常
                    }
                }
                markSkips(plan);
            }
        } finally {
            pool.shutdown();
        }
    }

    private void runOne(Task t, ExecutionPlan plan) {
        synchronized (EVENT_LOCK) {
            t.setStatus(Task.Status.RUNNING);
            events.emit("task_start", EventPayload.create("task_start")
                    .put("task", t.id()).put("title", t.title())
                    .put("deps", t.dependsOn()).build());
        }
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