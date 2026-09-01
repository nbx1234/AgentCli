# Day 19 · 上下文压缩 + Token 预算

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

长对话不爆窗：自动压缩 history，`/ctx` 可视化预算占用。Plan 模式子任务并行小升级。

## 任务清单

- [ ] 新增 `context/TokenEstimator.java`：CJK 字符≈1 token、ASCII≈1/4，保守上浮 10%
- [ ] 压缩阈值：`window(64k) - 摘要预留(8k) - 安全缓冲(6k)` ≈ 50k token 触发（.env 可调 `AGENTCLI_CONTEXT_WINDOW`）
- [ ] 新增 `context/HistoryCompactor.java`：保留 system + 最近 2 个 user 轮次完整内容，中间部分 LLM 摘要成 1 段（非流式调用）替换
- [ ] tool_call/tool_result 成对处理：摘要时可整体折叠为"期间调用了 X/Y/Z 工具，结论是…"
- [ ] `/ctx` 命令：估算 token / 占窗口百分比 / 距离阈值
- [ ] `/compact` 手动立即压缩
- [ ] Plan 模式：无依赖的 ready 任务并行执行（线程池 4，结果按原顺序收集）
- [ ] 测试：估算器边界（纯中文/纯英文/混合）；压缩保序断言

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/context/TokenEstimator.java | |
| 新增 | src/main/java/com/agentcli/context/HistoryCompactor.java | |
| 修改 | src/main/java/com/agentcli/agent/Agent.java | 每轮检查阈值 |
| 修改 | src/main/java/com/agentcli/Main.java | /ctx /compact |
| 修改 | src/main/java/com/agentcli/plan/ExecutionPlan.java | 并行执行 |
| 新增 | src/test/java/com/agentcli/context/TokenEstimatorTest.java | |

## 压缩时机伪代码

```java
if (estimator.estimate(history) > threshold) {
    emit("compact_start");
    history = compactor.compact(history);  // LLM 摘要中段
    emit("compact_end", 估算前后对比);
}
```

- 压缩是同步的（压缩完再继续当前轮），Web 时间线能看到 compact_start/end 事件

## 验收标准

1. 灌 60 轮长对话不报 context length exceeded，`/ctx` 显示压缩后回落
2. 压缩后问早期话题，LLM 能依据摘要给出模糊但方向正确的回答（不要求逐字记忆）
3. Plan 并行：5 个无依赖任务的总耗时明显小于串行（观察 task_start 时间戳重叠）

## 收尾发布

```bash
git add -A && git commit -m "Day 19: context compaction + parallel plan tasks" && git push
```

## 坑点提示

- 估算器宁大勿小：误触发压缩只是丢点细节，估小了是直接 API 报错
- 压缩期间用户 Ctrl+C → 压缩事务性：先写临时文件成功后再替换（或接受丢失，文档注明）
- 并行 SubAgent 共享 ToolRegistry 要确认无共享可变状态（Trace/Event 线程安全：synchronized 或并发队列）
