# Day 5 · ReAct 循环

> 状态：✅ 已完成（假工具闭环跑通，真工具与事件钩子见 Day 6）　|　实际日期：2026-09-05　|　commit：________

## 目标

think → act → observe 循环跑通，LLM 能连续调用工具后给出最终答案。这是整个项目的核心一跳。

## 任务清单

- [x] 新增 `agent/Agent.java`：`String run(String userInput, List<Message> history)`
- [x] 循环逻辑：调 LLM → 有 tool_calls 就执行 → 结果以 role=tool 回传 → 再调 LLM → 直到无 tool_calls 返回 content
- [x] 安全阀：最大迭代 10 次，超限抛错并回滚本轮消息
- [x] 临时注册 1 个假工具（`get_current_time`）验证闭环，真工具明天写
- [x] 过程输出：每步打印 `⚡ tool: name(args...)` 简洁行（后续接 Web）
- [x] 测试：mock ChatClient 返回"先 tool_call 再 final answer"，验证循环收敛且 message 序列正确

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/agent/Agent.java | ReAct 循环 |
| 修改 | src/main/java/com/agentcli/tool/ToolRegistry.java | 注册假工具 |
| 修改 | src/main/java/com/agentcli/Main.java | REPL 输入交给 Agent |
| 新增 | src/test/java/com/agentcli/agent/AgentLoopTest.java | mock 循环测试 |

## 循环伪代码

```java
while (iteration++ < MAX) {
    var resp = client.call(history, toolDefs);
    if (resp.toolCalls().isEmpty()) return resp.content();
    history.add(resp.assistantMessage());          // 带 tool_calls
    for (var tc : resp.toolCalls()) {
        String result = registry.execute(tc);      // Day 6 实现真工具
        history.add(Message.tool(tc.id(), result));
    }
}
return "达到最大迭代次数，任务未完成";
```

## 验收标准

1. 问"现在几点"，Agent 调 get_current_time 后正确回答
2. 不需要工具的问题（"你好"）一轮直接回答，不空转
3. `mvn test` 绿（mock 测试，不依赖网络）

## 收尾发布

```bash
git add -A && git commit -m "Day 5: ReAct loop with mock tool" && git push
```

## 坑点提示

- tool 结果永远以字符串回传，超长截断（如 8KB）并注明 truncated
- 假工具抛异常时，把异常消息作为 tool 结果回传给 LLM，让它自己调整——不要直接崩
- 这是本项目的架构核心日，代码宁可啰嗦也别绕；后面 Web 推送全靠这里埋事件钩子
