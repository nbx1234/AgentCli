package com.agentcli.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPlanTest {

    private List<Task> sample() {
        return List.of(
                new Task("t1", "调研", "读 README", List.of()),
                new Task("t2", "summary", "写 summary", List.of("t1")),
                new Task("t3", "todo", "写 todo", List.of("t1")));
    }

    @Test
    void topoOrderRespectsDependencies() {
        ExecutionPlan plan = new ExecutionPlan(sample());
        List<Task> order = plan.topoOrder();
        assertTrue(order.indexOf(plan.byId("t1")) == 0, "t1 无依赖应最先");
        assertTrue(order.indexOf(plan.byId("t2")) > order.indexOf(plan.byId("t1")), "t2 依赖 t1 应在其后");
        assertTrue(order.indexOf(plan.byId("t3")) > order.indexOf(plan.byId("t1")), "t3 依赖 t1 应在其后");
    }

    @Test
    void cycleDetectionThrows() {
        List<Task> cyclic = List.of(
                new Task("t1", "a", "p", List.of("t2")),
                new Task("t2", "b", "p", List.of("t1")));
        ExecutionPlan plan = new ExecutionPlan(cyclic);
        IllegalStateException e = assertThrows(IllegalStateException.class, plan::topoOrder);
        assertTrue(e.getMessage().contains("循环依赖"));
    }

    @Test
    void missingDependencyThrowsAtConstruction() {
        List<Task> bad = List.of(
                new Task("t1", "a", "p", List.of("ghost")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new ExecutionPlan(bad));
        assertTrue(e.getMessage().contains("ghost"));
    }

    @Test
    void readyTasksOnlyExposeSatisfiedOnes() {
        ExecutionPlan plan = new ExecutionPlan(sample());
        // 初始：仅 t1 就绪
        assertEquals(List.of("t1"), plan.readyTasks().stream().map(Task::id).toList());
        // t1 DONE 后 t2/t3 就绪
        plan.tasks().get(0).setStatus(Task.Status.DONE);
        assertEquals(List.of("t2", "t3"), plan.readyTasks().stream().map(Task::id).sorted().toList());
        assertFalse(plan.settled());
        // 全 DONE 后 settled
        plan.tasks().get(1).setStatus(Task.Status.DONE);
        plan.tasks().get(2).setStatus(Task.Status.DONE);
        assertTrue(plan.settled());
    }

    @Test
    void blockedDepMarksSkips() {
        ExecutionPlan plan = new ExecutionPlan(sample());
        plan.tasks().get(0).setStatus(Task.Status.FAILED);
        // t2/t3 的前置 t1 失败 → 均被阻塞
        assertTrue(plan.hasBlockedDep(plan.byId("t2")));
        assertTrue(plan.hasBlockedDep(plan.byId("t3")));
    }
}