# Day 13 · Trace 落盘（差异点② 启动）

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

每轮 ReAct 自动写成 JSONL trace——录制回放的地基。

## 任务清单

- [ ] 新增 `trace/TraceRecorder.java`：实现 EventEmitter 接口，每事件一行 JSON 追加写
- [ ] 文件路径：`~/.agentcli/traces/<yyyyMMdd-HHmmss>-<seq>.jsonl`（seq 防同秒并发）；首行写 meta 事件（user 输入、模型名、版本）
- [ ] `.env` 支持 `AGENTCLI_TRACE_DIR` 覆盖默认目录
- [ ] 新增 `/trace list`（列目录 + 每文件首行摘要）、`/trace show <id>`（格式化打印全事件）
- [ ] TraceRecorder 挂进 Main 的 EventEmitter 组（Console + Web + Trace 三路广播）
- [ ] 测试：写 5 个事件 → 读回逐行 parse 数量一致；目录不存在自动创建

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/trace/TraceRecorder.java | |
| 修改 | src/main/java/com/agentcli/Main.java | /trace 命令 + 三路广播 |
| 新增 | src/test/java/com/agentcli/trace/TraceRecorderTest.java | |

## JSONL 格式

```json
{"type":"meta","ts":...,"user":"帮我读README","model":"deepseek-chat","version":"0.0.1"}
{"type":"tool_call","ts":...,"iteration":1,"tool":"read_file","args":{...}}
{"type":"tool_result","ts":...,"tool":"read_file","result":"...","durationMs":12}
{"type":"turn_end","ts":...,"answer":"..."}
```

- 与 Web 事件共用同一 schema——这就是三路广播设计的好处，录制零额外成本

## 验收标准

1. 跑一个两步工具任务，traces 目录出现 jsonl，事件序列完整可回读
2. `/trace show <id>` 人类可读地打印全部步骤
3. `~/.agentcli/traces` 不存在时首跑自动创建

## 收尾发布

```bash
git add -A && git commit -m "Day 13: JSONL trace recording" && git push
```

## 坑点提示

- 用 BufferedWriter 逐事件 flush，进程被 Ctrl+C 也不丢已发生事件
- 大 tool_result 落盘时截断到 64KB（回放够用），完整内容本来就在文件系统里
- trace 含用户输入，目录在 `~` 下不进仓库；.gitignore 已有 `.traces/` 兜底
