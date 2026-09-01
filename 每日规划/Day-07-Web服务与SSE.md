# Day 7 · Javalin --web + SSE 端点（差异点① 启动）

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

`--web` 启动本地 HTTP 服务，浏览器能连上 SSE 收到事件。可视化路线正式开跑。

## 任务清单

- [ ] 新增 `web/EventEmitter.java` 接口：`emit(String type, Map<String,Object> payload)`
- [ ] 新增 `web/ConsoleSink.java`（现有打印迁进去）+ `web/WebSink.java`（SSE 广播）
- [ ] 新增 `web/WebServer.java`：Javalin 启动，监听 `AGENTCLI_WEB_PORT`（默认 8080）
- [ ] 路由：`GET /api/health` 返回 `{"ok":true}`；`GET /api/events` SSE，客户端连上先发一条 hello 事件
- [ ] `Main` 支持 `--web` 参数：带参数时 WebServer.start()，Agent 的 EventEmitter 挂上 WebSink
- [ ] Agent 循环里埋钩子：turn_start / llm_call / tool_call / tool_result / turn_end 各 emit 一条
- [ ] 测试：随机端口起 server，用 HttpURLConnection 连 `/api/health` 断言 200

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/web/EventEmitter.java | 事件接口 |
| 新增 | src/main/java/com/agentcli/web/ConsoleSink.java | 终端输出 |
| 新增 | src/main/java/com/agentcli/web/WebSink.java | SSE 广播 |
| 新增 | src/main/java/com/agentcli/web/WebServer.java | Javalin 封装 |
| 修改 | src/main/java/com/agentcli/agent/Agent.java | 埋事件钩子 |
| 修改 | src/main/java/com/agentcli/Main.java | --web 参数 |

## Javalin 6 要点

```java
var app = Javalin.create(cfg -> { /* 静态资源明天加 */ }).start(port);
app.get("/api/health", ctx -> ctx.json(Map.of("ok", true)));
app.sse("/api/events", client -> { /* 注册到 WebSink 的广播列表 */ });
```

- WebSink 持有 `CopyOnWriteArrayList<SseClient>`，断开的 client 要从列表移除（catch close 事件）
- emit 时 Jackson 序列化成 JSON 再 `client.send(event, json)`

## 验收标准

1. `java -jar ... --web` 后 `curl http://127.0.0.1:8080/api/health` 返回 ok
2. `curl -N http://127.0.0.1:8080/api/events` 挂着，CLI 里跑一个任务，能看到事件流推过来
3. 不带 `--web` 时行为与 Day 6 一致（零回归）

## 收尾发布

```bash
git add -A && git commit -m "Day 7: web server + SSE event sink" && git push
```

## 坑点提示

- Javalin 依赖 Jetty，会带来一堆传递依赖，shade 后 jar 变大是正常的
- SSE 事件名（event: 字段）前端要对应 addEventListener；嫌麻烦可以统一 message + type 字段
- 端口被占时 Javalin 抛异常，捕获后提示改 AGENTCLI_WEB_PORT
