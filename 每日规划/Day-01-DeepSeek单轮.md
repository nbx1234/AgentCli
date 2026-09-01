# Day 1 · DeepSeek 单轮 chat 接入

> 状态：✅ 已完成（待填 key 做真实调用验证）　|　实际日期：2026-09-01　|　commit：________

## 目标

REPL 输入一句话，DeepSeek 真实回复。LLM 链路从 0 到 1。

## 任务清单

- [x] 新增 `Env.java`：读项目根 `.env`（KEY=VALUE，忽略 `#` 注释），查找顺序：系统环境变量 → `.env`
- [x] 新增 `llm/Message.java`：`record Message(String role, String content)`
- [x] 新增 `llm/ChatClient.java` 接口：`String call(List<Message> messages)`
- [x] 新增 `llm/DeepSeekClient.java`：`HttpURLConnection` POST，Jackson 序列化/反序列化
- [x] REPL：`[echo]` 替换为真实调用；无 key 时提示"先在 .env 填 DEEPSEEK_API_KEY"
- [x] 测试：用写死的响应 JSON 测反序列化，不打真实网络

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/Env.java | .env 加载器 |
| 新增 | src/main/java/com/agentcli/llm/Message.java | 消息 record |
| 新增 | src/main/java/com/agentcli/llm/ChatClient.java | 客户端接口 |
| 新增 | src/main/java/com/agentcli/llm/DeepSeekClient.java | DeepSeek 实现 |
| 新增 | src/test/java/com/agentcli/llm/DeepSeekClientTest.java | 响应解析测试 |
| 修改 | src/main/java/com/agentcli/Main.java | REPL 接入 LLM |

## API 要点

```
POST {DEEPSEEK_BASE_URL}/chat/completions
Headers: Content-Type: application/json
         Authorization: Bearer <key>
Body:    {"model":"deepseek-chat","messages":[{"role":"user","content":"你好"}],"stream":false}
响应:    choices[0].message.content
```

- 超时设置：connect 10s / read 120s
- 请求体和解析只用 Jackson，`ObjectMapper` 做成静态单例

## 验收标准

1. `.env` 填好 key 后 `java -jar target/agentcli-0.0.1.jar`，输入 `你好` 得到真实回复
2. 不填 key 时启动正常，输入后给出友好提示而不是堆栈
3. `mvn test` 绿（测试不依赖网络）

## 收尾发布

```bash
cd /Users/gopnik/Code/GitHub/AgentCli
/opt/homebrew/bin/mvn clean package
git add -A && git commit -m "Day 1: DeepSeek single-turn chat" && git push
```

## 坑点提示

- API key 绝不能进测试或日志；`.env` 已在 .gitignore，提交前 `git status` 确认
- HttpURLConnection 对 4xx 不会抛异常，要用 `getErrorStream()` 读错误体
- deepseek 有时返回 `choices` 为空或 `finish_reason=length`，解析时判空
