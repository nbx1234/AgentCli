# Day 20 · 打磨：导出 / 文档 / 素材

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

发布日前夜：把"能用"变成"能给陌生人用"。

## 任务清单

- [ ] `/export`：当前会话导出 Markdown 到 `~/.agentcli/exports/session-<ts>.md`（含 system prompt 原文，便于审查 LLM 实际收到的指令）
- [ ] AGENTS.md：给协作者/Agent 的首读入口（结构 + 硬规则 + 验证命令，参考 paicli 风格写精简版）
- [ ] README 终稿：
  - 顶部 GIF（Day 9/15 录的素材，asciinema → SVG 或屏幕录制 → GIF）
  - 30 秒 Quick Start（cp .env.example → mvn package → java -jar --web）
  - 差异点 2+3 各一段 + 截图
- [ ] `.github/workflows/ci.yml`：push/PR 跑 `mvn test`（打 green badge），MCP 联调测试用 tag 排除
- [ ] 全仓库过一遍 TODO / 裸 System.out（统一走工具封装的输出，Web 模式下 stdout 会打架）
- [ ] CHANGELOG.md：v0.0.1 → v0.1.0 每日一句流水

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | Main.java | /export |
| 新增 | AGENTS.md | Agent 首读 |
| 重写 | README.md | 终稿 |
| 新增 | .github/workflows/ci.yml | CI |
| 新增 | CHANGELOG.md | |
| 修改 | docs/* | 截图/GIF 素材 |

## 验收标准

1. 全新 clone → 按 README 操作 → 5 分钟内跑通 Web 时间线 demo
2. CI 绿徽章出现在 README 顶部
3. `/export` 文件能独立读懂整场对话（含工具调用）

## 收尾发布

```bash
git add -A && git commit -m "Day 20: docs, export, CI" && git push
```

## 坑点提示

- GIF 控制在 3MB 内（imgflip/ezgif 压帧），否则 README 加载劝退
- CI 里不跑 MCP 联调（node 环境不可控），用 `mvn test -DexcludedGroups=mcp` 或 JUnit @Tag
- README 截图用深色终端 + 完整浏览器窗口，别只截代码
