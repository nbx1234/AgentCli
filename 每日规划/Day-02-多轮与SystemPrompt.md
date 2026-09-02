# Day 2 · 多轮对话历史 + System Prompt

> 状态：✅ 已完成（待填 key 做真实多轮验证）　|　实际日期：2026-09-02　|　commit：`2d9283e`

## 目标

LLM 记得上一轮说了什么；system prompt 里注入当前日期。

## 任务清单

- [x] REPL 维护 `List<Message> conversationHistory`，user / assistant 都追加
- [x] 每轮请求 messages = `[system] + history + [本轮 user]`；响应 assistant 消息回写 history
- [x] 新增 `prompt/SystemPrompt.java`：身份（AgentCli）、当前日期/时区、简洁规则
- [x] 新增命令：`/clear`（清空 history）、`/history`（打印条数和最近 3 条）
- [x] 测试：构造多轮消息验证请求体序列化含 system + 历史

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/prompt/SystemPrompt.java | system prompt 组装 |
| 修改 | src/main/java/com/agentcli/Main.java | history 维护 + 斜杠命令 |
| 新增 | src/test/java/com/agentcli/prompt/SystemPromptTest.java | 日期注入断言 |

## 设计要点

- system prompt 保持 < 500 字符，别把 token 浪费在废话上
- 日期用 `LocalDate.now()` + `Asia/Shanghai`，写明"用于理解相对日期"
- 斜杠命令分发建议先写成 if-chain（Day 4 之前别过度设计成命令表）

## 验收标准

1. 连续问"我叫 X" → "我叫什么"，第二答正确
2. `/clear` 后再问同样问题，LLM 说不知道
3. 问"今天是几号"，回答与系统日期一致

## 收尾发布

```bash
git add -A && git commit -m "Day 2: multi-turn history + system prompt" && git push
```

## 坑点提示

- assistant 回复必须回写 history，否则下一轮就"失忆"
- system prompt 每轮都重新生成（日期可能跨天），不要缓存
