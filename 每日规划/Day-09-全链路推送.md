# Day 9 · ReAct 全链路推送 + reasoning 预览

> 状态：✅ 已完成　|　实际日期：2026-09-10　|　commit：________

## 目标

浏览器里能看到一次任务的完整脉络：thinking、每次工具调用的参数与耗时、最终回答。Web 面板达到"可用 demo"水平。

## 任务清单

- [x] 事件 payload 标准化：新增 `web/EventPayload` 构造器，统一 `{type, ts, ...}`；ts 用 currentTimeMillis
- [x] Agent 埋点补全：`llm_start`/`llm_end`（含 durationMs，nanoTime 差值）、`tool_call`/`tool_result`（tool_result 含 exec durationMs）、iteration 编号
- [x] reasoning 透传：LlmResponse 加 reasoning 字段，DeepSeekClient 解析 `reasoning_content`，放进 `llm_end.preview`
- [x] `answer_delta`：最终回答按片切分聚合后逐条 emit（带累积 text），前端打字式渲染；`turn_end` 带完整 answer
- [x] 新增 `GET /api/dag` 占位：返回 `{"nodes":[],"edges":[]}`（Day 11 填真数据）
- [x] 前端：answer_delta 增量"You正在回答"气泡，llm_start/llm_end（含耗时/推理 gray 斜体）渲染
- [x] 测试：`EventSchemaTest`（所有事件 type 必填 + ts 可解析、llm_end/tool_result 含 durationMs、/api/dag 合法空数组）

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | src/main/java/com/agentcli/agent/Agent.java | 全链路埋点 |
| 修改 | src/main/java/com/agentcli/web/WebServer.java | /api/dag 占位 |
| 修改 | src/main/resources/web/index.html | 增量渲染 |
| 新增 | src/test/java/com/agentcli/web/EventSchemaTest.java | schema 校验 |

## 事件 Schema 约定

```json
{"type":"tool_call","ts":1725000000000,"iteration":2,
 "tool":"read_file","args":{"path":"README.md"},"durationMs":12,
 "preview":"README.md (393 lines)"}
```

- preview 字段是给时间线单行展示的截断摘要，前端不再自己截
- reasoning（DeepSeek reasoning_content）有就放进 `llm_end` 的 preview，没有就省略

## 验收标准

1. "分析 README 并写 summary.txt" 这类多步任务，浏览器完整呈现 ≥3 次工具调用 + 每步耗时
2. 回答逐字出现在浏览器里（虽然 CLI 也在刷）
3. `/api/dag` 返回合法空 DAG JSON

## 收尾发布

```bash
git add -A && git commit -m "Day 9: full ReAct event pipeline to web" && git push
```

## 坑点提示

- delta 事件别每 token 发一条——SSE 消息也有开销，聚合后 2-5 条/秒体验最好
- durationMs 用 System.nanoTime() 差值，别用 currentTimeMillis（可能回拨）
- 本日结束时录一段 asciinema + 屏幕录制存 docs/，Day 20 写 README 要用
