package com.agentcli.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行计划：任务集合 + 依赖图。
 *
 * 提供拓扑排序（Kahn，含环检测）与执行阶段需要的就绪任务查询。
 */
public final class ExecutionPlan {

    private final List<Task> tasks;
    private final Map<String, Task> byId;

    /** 校验依赖引用合法性；引用不存在的任务 id 会抛异常。 */
    public ExecutionPlan(List<Task> tasks) {
        this.tasks = List.copyOf(tasks);
        Map<String, Task> m = new LinkedHashMap<>();
        for (Task t : tasks) {
            m.put(t.id(), t);
        }
        for (Task t : tasks) {
            for (String dep : t.dependsOn()) {
                if (!m.containsKey(dep)) {
                    throw new IllegalArgumentException("依赖任务不存在：" + dep + "（被 " + t.id() + " 引用）");
                }
            }
        }
        this.byId = m;
    }

    public List<Task> tasks() {
        return tasks;
    }

    public Task byId(String id) {
        return byId.get(id);
    }

    /** Kahn 拓扑排序；存在环时抛 IllegalStateException。 */
    public List<Task> topoOrder() {
        Map<String, Integer> indeg = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (Task t : tasks) {
            indeg.putIfAbsent(t.id(), 0);
        }
        for (Task t : tasks) {
            for (String dep : t.dependsOn()) {
                indeg.merge(t.id(), 1, Integer::sum);
                adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(t.id());
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : indeg.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<Task> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            order.add(byId.get(id));
            for (String next : adj.getOrDefault(id, List.of())) {
                int nd = indeg.get(next) - 1;
                indeg.put(next, nd);
                if (nd == 0) {
                    queue.add(next);
                }
            }
        }
        if (order.size() != tasks.size()) {
            throw new IllegalStateException("计划存在循环依赖");
        }
        return order;
    }

    /** 就绪任务：PENDING 且所有依赖均 DONE。 */
    public List<Task> readyTasks() {
        List<Task> out = new ArrayList<>();
        for (Task t : tasks) {
            if (t.status() != Task.Status.PENDING) {
                continue;
            }
            if (hasPendingDep(t)) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    private boolean hasPendingDep(Task t) {
        for (String dep : t.dependsOn()) {
            Task d = byId.get(dep);
            if (d == null || d.status() != Task.Status.DONE) {
                return true;
            }
        }
        return false;
    }

    /** 是否还有未终结（PENDING 或 RUNNING）的任务。 */
    public boolean hasActive() {
        for (Task t : tasks) {
            Task.Status s = t.status();
            if (s == Task.Status.PENDING || s == Task.Status.RUNNING) {
                return true;
            }
        }
        return false;
    }

    /** 全部任务是否都已终结（DONE/FAILED/SKIPPED）。 */
    public boolean settled() {
        for (Task t : tasks) {
            Task.Status s = t.status();
            if (s == Task.Status.PENDING || s == Task.Status.RUNNING) {
                return false;
            }
        }
        return true;
    }

    /** 是否存在 FAILED/SKIPPED 的前置依赖（用于级联标 SKIPPED）。 */
    boolean hasBlockedDep(Task t) {
        for (String dep : t.dependsOn()) {
            Task d = byId.get(dep);
            if (d != null && (d.status() == Task.Status.FAILED || d.status() == Task.Status.SKIPPED)) {
                return true;
            }
        }
        return false;
    }
}