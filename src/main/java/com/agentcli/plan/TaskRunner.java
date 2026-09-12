package com.agentcli.plan;

/**
 * 单任务执行器：为给定任务跑出结论摘要。
 *
 * 由 SubAgent 实现；测试可注入 mock 以验证执行顺序与失败传播。
 */
@FunctionalInterface
public interface TaskRunner {

    /**
     * 执行任务并返回结论摘要。
     *
     * @param task 待执行任务（已置 RUNNING）
     * @param plan 整个计划（用于读取前置任务结论）
     * @return 任务结论；以 "[FAILED]" 开头表示执行失败
     * @throws Exception 任务执行抛出异常也表示失败
     */
    String run(Task task, ExecutionPlan plan) throws Exception;
}