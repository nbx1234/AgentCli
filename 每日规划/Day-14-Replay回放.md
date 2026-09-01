# Day 14 · /replay 回放

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

`/replay <id>` 重放一段 trace——先 dry-run 只演示，`--apply` 才真执行工具。差异化卖点的核心闭环。

## 任务清单

- [ ] 新增 `trace/TraceReplay.java`：读 jsonl → List<Event>，校验 meta 首行
- [ ] `/replay <id>` dry-run：按时间线打印每步（工具、参数、截断结果），结尾标注 `[dry-run] 未执行任何工具`
- [ ] `/replay <id> --apply`：逐步真实执行 tool_call，每步前显示参数并 y/n 确认（默认 n）；工具结果与原 trace 并排显示"本次结果 vs 录制时结果"
- [ ] apply 过程产生新的 EventEmitter 事件（Web 时间线同步可见）
- [ ] apply 结束把 replay 过程本身也录成一条新 trace（标记 type=replay, source=<原id>）
- [ ] 测试：replay 解析；dry-run 不触碰文件系统的断言（用临时目录 + 计数）

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/trace/TraceReplay.java | |
| 修改 | src/main/java/com/agentcli/Main.java | /replay 命令 |
| 新增 | src/test/java/com/agentcli/trace/TraceReplayTest.java | |

## dry-run 输出样式

```
▶ 回放 trace 20260901-143022-1 (录制于 2026-09-01 14:30)
  1. tool_call read_file {"path":"README.md"}          ✓
  2. tool_call write_file {"path":"summary.txt",...}   ✓ (dry-run 未写入)
  3. turn_end "已生成 summary..."
[dry-run] 未执行任何工具。加 --apply 真实执行（逐步确认）
```

## 验收标准

1. dry-run 后工作目录无任何文件变动（用 git status 验证）
2. --apply 时拒绝某步（n）→ 该步跳过，后续照常询问
3. apply 写入文件与录制时同路径同内容（简单任务）

## 收尾发布

```bash
git add -A && git commit -m "Day 14: /replay dry-run + apply" && git push
```

## 坑点提示

- replay 的 write_file 依然过 PathGuard + y/n 确认——回放不是特权通道，这是和"危险宏"的本质区别
- 原结果的比对只做展示不做断言（环境变了结果本就不同），避免误导
- trace id 手敲容易错，`/trace list` 输出可直接复制
