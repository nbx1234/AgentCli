# Day 3 · SSE 流式输出

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

回答像 Claude Code 一样逐字蹦出来，而不是憋 10 秒吐一大段。

## 任务清单

- [ ] `ChatClient` 加流式方法：`void callStream(List<Message> messages, Consumer<String> onDelta)`
- [ ] `DeepSeekClient` 实现：请求体 `"stream":true`，逐行读 SSE
- [ ] SSE 解析：`data: {...}` 行 → `choices[0].delta.content` 累加；`data: [DONE]` 结束
- [ ] REPL 打印：每个 delta 直接 `System.out.print` + flush，结束后补 `\n`
- [ ] 非流式 `call()` 保留（Planner/压缩后面要用非流式）
- [ ] 测试：喂一段模拟 SSE 文本流，验证拼接结果正确

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 修改 | src/main/java/com/agentcli/llm/ChatClient.java | 加 callStream |
| 修改 | src/main/java/com/agentcli/llm/DeepSeekClient.java | SSE 实现 |
| 修改 | src/main/java/com/agentcli/Main.java | REPL 用流式 |
| 新增 | src/test/java/com/agentcli/llm/SseParserTest.java | SSE 解析测试 |

## SSE 格式速查

```
data: {"choices":[{"delta":{"content":"你"}}]}
data: {"choices":[{"delta":{"content":"好"}}]}
data: [DONE]
```

- 把解析器抽成独立的 `SseParser`（静态方法或小类），方便单测
- 每行 delta 的 content 可能为 `null`（只有 role 的首帧），要判空跳过

## 验收标准

1. 长回答（>200 字）能观察到逐字出现
2. 网络中断时打印错误并保留已收到的部分，不崩溃
3. `/history` 显示的 assistant 内容 = 流式拼接的完整文本

## 收尾发布

```bash
git add -A && git commit -m "Day 3: SSE streaming output" && git push
```

## 坑点提示

- DeepSeek 国内网络下 HTTP/2 长流可能被重置（PaiCLI 踩过）：HttpURLConnection 默认就是 HTTP/1.1，反而稳
- BufferedReader.readLine 会阻塞到整行，SSE 恰好是行分隔的，直接用即可
- 退出（Ctrl+C / :quit）时记得关闭 reader 线程，别留僵尸连接
