# Day 12 · Plan 审阅交互

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

`/plan` 生成后不立即执行：人审阅 → 选 run / 补充 / 取消。对齐 Claude Code 的 plan review 体验。

## 任务清单

- [ ] 生成计划后进入审阅态，提示：`[r] 执行 / [i] 补充要求重新规划 / [c] 取消`
- [ ] `r`：按 Day 11 流程执行
- [ ] `i`：读一行补充指令 → 连同原计划一起喂回 Planner 重新生成（最多重规划 3 次防循环）
- [ ] `c`：放弃计划，回到普通对话
- [ ] 输入用现有 BufferedReader 即可（读单字符再确认，或读整行），不引 JLine——保持依赖精简
- [ ] `GET /api/dag` 填真数据：nodes=任务（含 status 颜色），edges=dependsOn；前端把空占位换成 SVG 简易 DAG 图
- [ ] 测试：审阅输入解析（r/i/c + 大小写 + 非法输入重问）

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | src/main/java/com/agentcli/Main.java | 审阅态逻辑 |
| 新增 | src/main/java/com/agentcli/plan/PlanReviewParser.java | 输入解析 |
| 修改 | src/main/java/com/agentcli/web/WebServer.java | /api/dag 真数据 |
| 修改 | src/main/resources/web/index.html | SVG DAG |
| 新增 | src/test/java/com/agentcli/plan/PlanReviewParserTest.java | |

## 交互细节

```
📋 计划（3 个任务）：
  t1 调研 README 结构            依赖: -
  t2 生成 summary.txt            依赖: t1
  t3 生成 todo.md                依赖: t1
[r]执行  [i]补充要求  [c]取消  >
```

- 非法输入友好重问，不要抛异常退栈
- `i` 的补充指令要回显确认后再重新规划

## 验收标准

1. `c` 取消后 conversationHistory 不残留计划相关内容
2. `i` 输入"todo.md 还要按优先级排序"后重新生成，新计划体现该要求
3. Web 端 /api/dag 能看到带依赖箭头的任务图（SVG 直线即可，不追曲线美观）

## 收尾发布

```bash
git add -A && git commit -m "Day 12: plan review interaction + DAG view" && git push
```

## 坑点提示

- 审阅期间用户可能直接 Ctrl+D（EOF）——readLine 返回 null 要当 c 处理
- SVG 画 DAG 用分层布局（按拓扑深度分 y 轴），20 行 JS 就够，别引入 dagre
- 这天改动交互多，commit 前**手工完整过一遍**三种路径
