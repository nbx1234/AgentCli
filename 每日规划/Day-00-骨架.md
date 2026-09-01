# Day 0 · 项目骨架

> 状态：✅ 已完成　|　实际日期：2026-08-31　|　commit：`7de9930`

## 目标

工具链贯通：Maven 能出 fat jar，Banner + REPL 能跑，仓库推上 GitHub。

## 任务清单

- [x] `git init`（main 分支）+ 目录结构 `src/main/java/com/agentcli`
- [x] pom.xml：Java 17 + shade fat jar，预埋 Javalin / Jackson / SLF4J / JUnit 5
- [x] Main.java：Banner + REPL（`:help` / `:version` / `:quit`，其余回显）
- [x] MainTest：版本号 + Banner 断言
- [x] README.md（定位 + 21 天进度条）、ROADMAP.md（差异点详述）
- [x] .env.example / .gitignore
- [x] `mvn clean package` 绿 + `java -jar` 验证 Banner
- [x] gh repo create + push

## 产出

- 仓库：https://github.com/nbx1234/AgentCli
- 产物：`target/agentcli-0.0.1.jar`（7.8M fat jar）
- git 身份配在仓库 local config（`~/.gitconfig` 被沙箱挡）

## 坑点记录

- 沙箱 PATH 不含 `/opt/homebrew/bin`，调用 `mvn` / `gh` 需绝对路径
- 沙箱写不了 `~/.gitconfig`，git 身份用 `git config user.name`（仓库级）
- Banner 是手拼 ASCII，测试断言用 "Agent" 而非 "AgentCli"（字面量拼不出来）
