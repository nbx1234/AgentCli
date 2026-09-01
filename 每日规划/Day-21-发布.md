# Day 21 · v1.0.0 发布

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

全量回归 → 打 tag → GitHub Release。21 天冲刺收官。

## 任务清单

- [ ] `mvn clean package` + `mvn test` 全绿（含 mcp tag 需本地手动跑一次）
- [ ] 手工回归清单逐项过：
  - [ ] 普通对话（流式）
  - [ ] 工具任务：读/写/执行三件套
  - [ ] /plan 全流程（生成→审阅→执行→DAG 图）
  - [ ] trace 录制 → /replay dry-run → --apply
  - [ ] /skill save → run
  - [ ] MCP echo + filesystem
  - [ ] HITL：allow/ask/deny 三种路径 + /audit
  - [ ] 长对话压缩 + /ctx
  - [ ] --web 断连重连
- [ ] 版本号统一 1.0.0：pom.xml + Main.VERSION + README 进度条改 `完成 21/21`
- [ ] ROADMAP.md 勾掉全部已完成项
- [ ] `git tag v1.0.0 && git push --tags`
- [ ] GitHub Release：
  ```bash
  gh release create v1.0.0 --title "AgentCli v1.0.0" --notes "..." target/agentcli-1.0.0.jar
  ```
  附上 fat jar + Release notes（亮点：Web 时间线 / 录制即技能 / 单文件前端）

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | pom.xml | 1.0.0 |
| 修改 | Main.java | VERSION |
| 修改 | README.md / ROADMAP.md | 收官状态 |

## 发布 notes 模板

```markdown
## AgentCli v1.0.0 — 可视化 ReAct + 录制回放即技能

### 亮点
- 🖥️ Web 时间线：CLI 之外实时观看 Agent 每一步（单文件前端，零框架）
- 🎙️ 录制即技能：每轮自动落 trace，/replay 回放，/skill save 变可复用技能
- 🗂️ Plan+DAG：任务拆解、依赖执行、SVG 依赖图
- 🔌 MCP 生态：stdio 接入外部工具与资源
- 🛡️ HITL：只读放行 / 写入必审 / 全量审计

### 快速开始
cp .env.example .env && mvn clean package
java -jar target/agentcli-1.0.0.jar --web
```

## 验收标准

1. Release 页面有 jar 附件 + 完整 notes
2. 下载 jar 在干净机器（有 Java 17）直接跑通
3. 庆祝 🎉

## 收尾发布

```bash
git add -A && git commit -m "Day 21: release v1.0.0"
git tag v1.0.0 && git push --tags
gh release create v1.0.0 --title "AgentCli v1.0.0" --notes-file RELEASE_NOTES.md target/agentcli-1.0.0.jar
```

## 坑点提示

- tag 打在 commit 上先 `git push` 主分支再 `push --tags`，顺序乱了 Release 关联不到 commit
- jar 附件名含版本号，与 pom 一致
- 发布后发一条朋友圈/Twitter/即刻——21 天连续交付本身就是这个项目的作品
