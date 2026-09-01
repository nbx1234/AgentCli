# Day 17 · Filesystem MCP 实战 + resources 提及

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

接官方 filesystem server 实战，并支持 `@server:uri` 引用 MCP resources——让外部数据像本地文件一样自然。

## 任务清单

- [ ] mcp.json 配 `@modelcontextprotocol/server-filesystem` 指向一个 demo 目录；README 写清一行启动命令
- [ ] resources：`resources/list` → 每个资源注册成虚拟工具 `mcp__<server>__resource__<uri>`（调用即 read）
- [ ] `@fs:readme` 式 mention：输入含 `@<server>:<uri>` 时先 read 内容内联为 `<resource>` 块再进 Agent
- [ ] PathGuard 对 MCP filesystem server 不生效（沙箱由 server 启动参数限定），但在工具 description 里写明"仅限 demo 目录"让 LLM 有边界感
- [ ] Demo 数据：demo 目录放 2 个 md + 1 个 json，验收用
- [ ] 测试：mention 展开器单测（命中/未命中/转义）

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | src/main/java/com/agentcli/mcp/McpServerManager.java | resources 支持 |
| 新增 | src/main/java/com/agentcli/mcp/MentionExpander.java | @server:uri 展开 |
| 修改 | src/main/java/com/agentcli/Main.java | 输入预处理挂 mention |
| 修改 | README.md | MCP 快速开始段 |
| 新增 | demo/mcp-fs/*.md,*.json | 靶子数据 |

## 验收标准

1. `总结 @fs:demo/mcp-fs/notes.md 的要点` → resource 内容内联，回答正确
2. 不配置任何 MCP 时启动和普通对话完全不受影响
3. README 的 MCP 段照做 5 分钟内能跑通（换台机器 mindset 自测）

## 收尾发布

```bash
git add -A && git commit -m "Day 17: MCP filesystem + resource mentions" && git push
```

## 坑点提示

- server-filesystem 是 npx 包，node 版本 ≥18；机器没有 node 就改用 Python 实现的 filesystem server，README 写两个选项
- resources/list 很多 server 返回空（不支持），要当"可选能力"处理
- mention 展开放在 history 追加**之前**，且展开后的原文不进 trace 的 user 字段（trace 记录用户原始输入）
