# Day 16 · MCP Client（stdio）

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

连接外部 MCP server，动态工具注册进 ToolRegistry。生态大门打开。

## 任务清单

- [ ] 新增 `mcp/McpClient.java`：子进程 stdio + 换行分隔 JSON-RPC 2.0
  - `initialize` → `notifications/initialized` → `tools/list` → `tools/call`
- [ ] 新增 `mcp/McpServerManager.java`：读 `~/.agentcli/mcp.json`：
  ```json
  {"mcpServers":{"fs":{"command":"npx","args":["-y","@modelcontextprotocol/server-filesystem","/tmp/mcp-demo"]}}}
  ```
- [ ] 工具注册：每个 MCP tool 以 `mcp__<server>__<tool>` 进入 ToolRegistry，schema 从 tools/list 转换
- [ ] `/mcp` 命令：列 server 连接状态 + 工具数
- [ ] 写一个 20 行的 Node echo server（`examples/mcp-echo.js`）做联调靶子：add(a,b)、echo(text) 两个工具
- [ ] 测试：echo server 真实启动联调（标记 @Tag("mcp")，CI 可跳过）；JSON-RPC 编解码单测

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/mcp/McpClient.java | |
| 新增 | src/main/java/com/agentcli/mcp/McpServerManager.java | |
| 新增 | examples/mcp-echo.js | 联调靶子 |
| 修改 | src/main/java/com/agentcli/Main.java | /mcp 命令 |
| 新增 | src/test/java/com/agentcli/mcp/JsonRpcTest.java | |

## JSON-RPC 速查

```json
→ {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"agentcli","version":"0.0.1"}}}
← {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","serverInfo":{...},"capabilities":{"tools":{}}}}
→ {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"add","arguments":{"a":1,"b":2}}}
← {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"3"}]}}
```

- 请求 id 自增；每请求独立 id，响应按 id 配对（今天串行发送，不做并发管道）

## 验收标准

1. `node examples/mcp-echo.js` + mcp.json 配置后启动 AgentCli，`/mcp` 显示 2 个工具
2. 问"3 加 5 等于几"，Agent 调 `mcp__echo__add` 得 8
3. server 进程死了，调用返回友好错误，主程序不崩

## 收尾发布

```bash
git add -A && git commit -m "Day 16: MCP stdio client + dynamic tools" && git push
```

## 坑点提示

- MCP stdio 是**换行分隔** JSON（不是 LSP 的 Content-Length 帧），别搞混
- 子进程 stderr 要及时消费（读掉丢弃或落日志），否则管道写满 server 会被卡死
- npx 首次下载包很慢，联调时先把包安装好或直接 node 本地脚本
