# Day 11 · DAG 执行 + SubAgent

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

计划按拓扑序真正执行，每个任务由独立上下文的 SubAgent 完成。

## 任务清单

- [ ] 新增 `agent/SubAgent.java`：每个任务独立 conversationHistory（system = 任务 prompt + 全局工具说明），复用 Agent 的 ReAct 循环和 ToolRegistry
- [ ] ExecutionPlan 驱动：就绪任务（依赖全 done）逐个执行，结果（task 结论摘要）写回
- [ ] 后续任务的 system prompt 注入"前置任务结论"段落，让 t2 能引用 t1 的产出
- [ ] 任务状态机：PENDING → RUNNING → DONE / FAILED；FAILED 时下游任务标记 SKIPPED
- [ ] 全程 emit 事件：task_start / task_end（前端时间线能看到每个任务的子步骤）
- [ ] 测试：mock Agent 验证执行顺序 = 拓扑序、失败传播正确

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/agent/SubAgent.java | 任务执行器 |
| 修改 | src/main/java/com/agentcli/plan/ExecutionPlan.java | 状态机 + 就绪计算 |
| 修改 | src/main/java/com/agentcli/Main.java | /plan 审阅后执行（今天先自动跑） |
| 新增 | src/test/java/com/agentcli/agent/SubAgentOrderTest.java | 顺序/失败测试 |

## 执行模型

```
plan.topoOrder();
while (有 PENDING 任务) {
    ready = 依赖全 DONE 的任务;
    for (task : ready) { SubAgent.run(task); }   // 今天串行，明天可并行
}
```

- SubAgent 的工具调用结果**不进**主会话 history，只回传最终结论——不然主上下文爆炸
- 任务间传递靠"前置结论注入"，这是 plan 模式的价值所在

## 验收标准

1. `/plan` 生成的 3 任务计划完整执行，顺序正确（观察 task_start 时间戳）
2. 中间任务失败（如 prompt 里故意写"调用不存在的工具"）时，下游显示 SKIPPED，Agent 不死循环
3. Web 时间线能看到 task_start/task_end 嵌套在 turn 事件里

## 收尾发布

```bash
git add -A && git commit -m "Day 11: DAG execution + SubAgent" && git push
```

## 坑点提示

- SubAgent 迭代上限同样 10 次，防单任务死循环拖垮整个 plan
- FAILED 的判定：SubAgent 返回的结论以 `[FAILED]` 开头，或达到迭代上限
- 今天串行执行就好，并行留到 Day 19 打磨期做（先把正确性立住）
