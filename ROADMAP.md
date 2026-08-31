# AgentCli Roadmap

> 状态：演进方向，不代表已交付。"已交付"以 README 进度条为准。

## 差异化定位

对标 PaiCLI（已交付 23 期），但避开它的强项（CLI 渲染 / 多 provider / RAG / 多 agent 协作），走两条新路线：

### 差异点 1 · 可视化（Day 7-15）

PaiCLI 是纯 CLI + TUI，看 ReAct 全靠滚动文字。AgentCli 起一个本地 Web 服务：

- `GET /api/trace` SSE 推送当前 ReAct 的每一步（thinking / tool_call / tool_result / answer）
- `GET /api/dag` 返回 Plan 模式的 DAG JSON，前端画成依赖图
- 前端 vanilla JS + 一点 SVG，不引框架，单页够用
- CLI 仍是主入口，Web 面板是 `--web` 启动时的可选并排视图

### 差异点 2 · 录制即技能（Day 13-21）

PaiCLI 的 skill 是手写 markdown。AgentCli 把"做过的事"自动变成 skill：

- 每轮 ReAct 写 `~/.agentcli/traces/<ts>.jsonl`，逐事件落盘
- `/replay <id>` 重放工具调用（dry-run 模式不真实执行，`--apply` 才执行）
- `/skill save <name> <traceId>` 把 trace 抽象成参数化模板，下次同类任务可注入
- trace 可导出成单文件分享给团队

## 21 天计划（每天一个 commit，每周一个 tag）

### Day 0 · 骨架 ✅ v0.0.1
- [x] pom.xml + Maven shade fat jar
- [x] Main + Banner + REPL 回显
- [x] README / .env.example / .gitignore
- [x] git init + 第一个 commit + GitHub 仓库

### Day 1-3 · LLM 调通
- [ ] Day 1：DeepSeek 单轮 chat（`ChatClient.call(messages)`）
- [ ] Day 2：多轮对话历史 + 基础 system prompt 注入
- [ ] Day 3：SSE 流式输出 + reasoning 字段保留

### Day 4-6 · ReAct 骨架
- [ ] Day 4：tool schema 定义 + tool_call 解析
- [ ] Day 5：ReAct 循环（think → act → observe → repeat）
- [ ] Day 6：3 内置工具（read_file / write_file / execute_command）

### Day 7-9 · 差异点① Web 面板
- [ ] Day 7：Javalin 起 `--web` 模式 + `/api/trace` SSE
- [ ] Day 8：前端单页 + 时间线组件
- [ ] Day 9：把 ReAct 步骤实时推到前端

### Day 10-12 · Plan + DAG
- [ ] Day 10：Planner 生成 Task 列表
- [ ] Day 11：ExecutionPlan 拓扑排序 + SubAgent 执行
- [ ] Day 12：Plan 审阅交互（Enter/I/ESC）

### Day 13-15 · 差异点② 录制回放
- [ ] Day 13：trace jsonl 落盘
- [ ] Day 14：`/replay <id>` dry-run 重放
- [ ] Day 15：`/skill save <name>` 把 trace 抽象成模板

### Day 16-18 · MCP 接入
- [ ] Day 16：McpClient + stdio transport
- [ ] Day 17：filesystem 示例 server
- [ ] Day 18：HITL 审批策略（只读默认放行）

### Day 19-21 · 打磨
- [ ] Day 19：记忆压缩阈值 + token 预算
- [ ] Day 20：Banner / `/export` / 文档完善
- [ ] Day 21：v1.0.0 release

## 不在路线图

- 多 provider 适配（先 DeepSeek 一个跑通）
- RAG / 向量检索
- Multi-Agent 协作
- 微信 / 即时通讯通道
- Chrome DevTools / 浏览器自动化

这些 PaiCLI 已经做过，不重复。
