# AgentCli

> 面向 vibe coding 的 Java Agent CLI：**可视化 ReAct + 录制回放即技能**

对标 [PaiCLI](https://github.com/) 的同类产品，但走两条差异化路线：

- **可视化**：CLI 之外附带一个 Web 面板，实时把 ReAct 步骤、tool call、reasoning 推到浏览器，DAG / 时间线一眼看穿
- **录制即技能**：每轮 ReAct 自动落盘成可回放 / 可分享的 trace，`/replay <id>` 复现工具链，`/skill save <name>` 把 trace 转成可复用 skill 模板

## 当前状态

**Day 17 · v0.0.1** — MCP filesystem 接入 + `@server:uri` 资源提及，外部数据像本地文件一样可读。

进度条：`[███████░░░] 17/21`

## 快速开始

```bash
cp .env.example .env      # 填入至少一个 provider key
mvn clean package         # 产出 target/agentcli-0.0.1.jar
java -jar target/agentcli-0.0.1.jar
```

进入 REPL 后：
- `:help` 看命令
- `:version` 看版本
- `:quit` 退出

## MCP 快速开始（5 分钟内跑通）

1. 写好 `~/.agentcli/mcp.json`（示例指向仓库自带 demo 目录）：
   ```json
   {"mcpServers":{"fs":{"command":"npx","args":["-y","@modelcontextprotocol/server-filesystem","demo/mcp-fs"]}}}
   ```
2. 启动 AgentCli 后执行 `/mcp`，看到 `已连接 fs：N 个工具/资源`。
3. 直接问：`总结 @fs:demo/mcp-fs/notes.md 的要点` —— 提及会被内联成 `<resource>` 块再进 Agent。

> 没有 node 时：可换成 Python 版 filesystem server（`uvx mcp-server-filesystem demo/mcp-fs`），
> 或 `AGENTCLI_MCP_CONFIG=/path/to/mcp.json` 指定配置位置。
> 不配置 MCP 时普通对话完全不受影响。

## 21 天路线图

| Day | 主题 | 交付 |
|-----|------|------|
| 0 | 骨架 | pom + Main + Banner + REPL 回显 |
| 1-3 | LLM 调通 | 单轮 → 多轮 → 流式 SSE |
| 4-6 | ReAct 骨架 | tool_call 循环 + 3 内置工具 |
| 7-9 | **差异点① Web 面板** | Javalin + SSE 推送 + 前端时间线 |
| 10-12 | Plan + DAG | Planner / ExecutionPlan / 审阅交互 |
| 13-15 | **差异点② 录制回放** | trace jsonl + `/replay` + `/skill save` |
| 16-18 | MCP 接入 | stdio transport + filesystem 示例 |
| 19-21 | 打磨 | 记忆压缩 / token 预算 / Banner / 文档 |

详见 [ROADMAP.md](ROADMAP.md)。

## 技术栈

- Java 17 + Maven（shade fat jar）
- Javalin（Web UI / SSE）
- Jackson（JSON）
- SLF4J-simple（日志）
- JUnit 5（测试）

## 开发

```bash
mvn test                 # 全量
mvn test -Dtest=XxxTest  # 针对性
```

## License

MIT
