package com.agentcli.plan;

import com.agentcli.web.EventEmitter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecutorTest {

    private ExecutionPlan sample() {
        return new ExecutionPlan(List.of(
                new Task("t1", "调研", "读 README", List.of()),
                new Task("t2", "summary", "写 summary", List.of("t1")),
                new Task("t3", "todo", "写 todo", List.of("t1"))));
    }

    /** 记录执行顺序并把结论写回任务的 mock runner。 */
    private final class RecordingRunner implements TaskRunner {
        final List<String> order = new ArrayList<>();

        @Override
        public String run(Task task, ExecutionPlan plan) throws Exception {
            order.add(task.id());
            return "结论[" + task.id() + "]";
        }
    }

    @Test
    void executesInTopologicalOrder() throws Exception {
        ExecutionPlan plan = sample();
        RecordingRunner runner = new RecordingRunner();
        new PlanExecutor(runner, EventEmitter.NOOP).execute(plan);

        // Day 19 并行执行：依赖序仍保证（t1 先于 t2/t3），但同层兄弟（t2/t3）顺序不保证
        assertEquals("t1", runner.order.get(0));
        assertTrue(runner.order.containsAll(List.of("t1", "t2", "t3")));
        assertEquals(Task.Status.DONE, plan.byId("t1").status());
        assertEquals(Task.Status.DONE, plan.byId("t2").status());
        assertEquals(Task.Status.DONE, plan.byId("t3").status());
        assertTrue(plan.byId("t3").conclusion().contains("t3"));
    }

    @Test
    void failedUpstreamSkipsDownstream() throws Exception {
        ExecutionPlan plan = sample();
        TaskRunner runner = (task, p) -> {
            if ("t1".equals(task.id())) {
                throw new IllegalStateException("boom");
            }
            return "ok";
        };
        new PlanExecutor(runner, EventEmitter.NOOP).execute(plan);

        assertEquals(Task.Status.FAILED, plan.byId("t1").status());
        assertEquals(Task.Status.SKIPPED, plan.byId("t2").status());
        assertEquals(Task.Status.SKIPPED, plan.byId("t3").status());
    }

    @Test
    void prefixedFailedConclusionIsTreatedAsFailure() throws Exception {
        ExecutionPlan plan = sample();
        TaskRunner runner = (task, p) -> "t1".equals(task.id()) ? "[FAILED] 挂了" : "ok";
        new PlanExecutor(runner, EventEmitter.NOOP).execute(plan);

        assertEquals(Task.Status.FAILED, plan.byId("t1").status());
        assertEquals(Task.Status.SKIPPED, plan.byId("t2").status());
    }

}