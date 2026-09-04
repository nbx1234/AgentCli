# Day 4 · Tool Schema + tool_call 解析

> 状态：✅ 已完成（解析层完成，执行层见 Day 5）　|　实际日期：2026-09-04　|　commit：________

## 目标

请求能带上工具定义，响应能解析出 tool_calls——为 ReAct 循环铺路。今天只解析不执行。

## 任务清单

- [x] 新增 `tool/ToolDefinition.java`：name / description / JSON Schema parameters，可序列化为 OpenAI tools 格式
- [x] 新增 `tool/ToolCall.java`：`record ToolCall(String id, String name, String argumentsJson)`
- [x] 新增 `tool/ToolRegistry.java`：register / lookup / listDefinitions()（工具实现明天写，今天空壳）
- [x] `ChatClient.call` 重载：接受 `List<ToolDefinition>`；响应解析出 `message.tool_calls`（经 LlmResponse 携带）
- [x] Message 扩展：支持携带 tool_calls 和 tool_call_id（role=tool），不再是纯 content record
- [x] 测试：canned 响应解析出 2 个 tool_call，arguments JSON 可反序列化成 Map

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/tool/ToolDefinition.java | 工具 schema |
| 新增 | src/main/java/com/agentcli/tool/ToolCall.java | 工具调用记录 |
| 新增 | src/main/java/com/agentcli/tool/ToolRegistry.java | 注册表 |
| 修改 | src/main/java/com/agentcli/llm/Message.java | 扩展 tool_calls 字段 |
| 修改 | src/main/java/com/agentcli/llm/DeepSeekClient.java | 请求带 tools、解析 tool_calls |
| 新增 | src/test/java/com/agentcli/tool/ToolCallParseTest.java | 解析测试 |

## 请求/响应格式速查

```json
// 请求 tools 字段
{"type":"function","function":{"name":"read_file","description":"读文件",
  "parameters":{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}}}

// 响应 tool_calls
choices[0].message.tool_calls[0] = {"id":"call_xxx","type":"function",
  "function":{"name":"read_file","arguments":"{\"path\":\"README.md\"}"}}
```

- `arguments` 是 **JSON 字符串**不是对象，要二次解析
- 下一轮回传时：assistant 消息带 tool_calls，然后每个 tool 一条 `{"role":"tool","tool_call_id":id,"content":结果}`

## 验收标准

1. 请求体带 tools 后，问"帮我读 README"，LLM 会返回 tool_call（虽然还没执行，能看到解析日志即达标）
2. arguments 二次解析为 Map 后 key 齐全
3. 不带 tools 的普通请求行为与 Day 3 完全一致（不回归）

## 收尾发布

```bash
git add -A && git commit -m "Day 4: tool schema + tool_call parsing" && git push
```

## 坑点提示

- Message 从 record 改 class 时注意 Jackson 反序列化注解，别把 null 字段序列化进请求体
- tool_call 的 id 必须原样回传，role=tool 消息对不上 id 会被 API 拒绝
