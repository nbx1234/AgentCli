# Day 10 · Planner 生成任务列表

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

`/plan <目标>` 让 LLM 输出结构化 JSON 任务列表，含依赖关系。

## 任务清单

- [ ] 新增 `plan/Task.java`：id / title / prompt / dependsOn(List<String>) / status
- [ ] 新增 `plan/ExecutionPlan.java`：tasks + 依赖图，提供 `topoOrder()`（Kahn 算法）+ 环检测
- [ ] 新增 `plan/Planner.java`：LLM 一次性调用（非流式），prompt 要求只输出 JSON
- [ ] Jackson 解析 Planner 输出 → ExecutionPlan；解析失败重试 1 次（prompt 里补"你上次输出不是合法 JSON"）
- [ ] 新增 `/plan <goal>` 命令：生成后按拓扑序打印任务表（id / 标题 / 依赖）
- [ ] 测试：canned JSON 解析、拓扑排序正确性、环检测抛错

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/plan/Task.java | |
| 新增 | src/main/java/com/agentcli/plan/ExecutionPlan.java | |
| 新增 | src/main/java/com/agentcli/plan/Planner.java | |
| 修改 | src/main/java/com/agentcli/Main.java | /plan 命令 |
| 新增 | src/test/java/com/agentcli/plan/ExecutionPlanTest.java | 排序/环测试 |

## Planner Prompt 骨架

```
你是任务规划器。把用户目标拆解为 2-6 个任务，输出严格 JSON：
{"tasks":[{"id":"t1","title":"...","prompt":"给执行者的完整指令","dependsOn":[]}]}
不要输出 JSON 以外的任何文字。
用户目标：{goal}
当前日期：{date}
```

- prompt 字段要写成"给执行者的自包含指令"，Planner 和执行者是两个 LLM 视角
- 温度建议 0.2，规划要稳定不要发挥

## 验收标准

1. `/plan 调研 README 并生成 summary 和 todo 两个文件` 得到 3 个任务，summary/todo 依赖调研任务
2. 构造环依赖（t1→t2→t1）时明确报错"计划存在循环依赖"
3. Planner 输出混入 ```json 围栏时也能解析（先剥围栏再 parse）

## 收尾发布

```bash
git add -A && git commit -m "Day 10: planner + ExecutionPlan" && git push
```

## 坑点提示

- LLM JSON 里 dependsOn 引用不存在的 id → 解析后校验，缺失即重试
- 今日只生成不执行；`/plan` 打印任务表后停下，执行逻辑明天写
