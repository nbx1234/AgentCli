# Day 8 · 前端单页 + 事件时间线

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

浏览器打开 http://127.0.0.1:8080 ，看到实时滚动的事件时间线。不引任何前端框架。

## 任务清单

- [ ] `src/main/resources/web/index.html`：单文件页面（内联 CSS/JS）
- [ ] EventSource 连 `/api/events`，按 type 渲染卡片：
  - `turn_start` 蓝色、`tool_call` 橙色（显示工具名 + 参数摘要）、`tool_result` 灰色（可折叠）、`turn_end` 绿色
- [ ] 顶部状态栏：连接状态圆点（绿=已连，红=断开，自动重连 EventSource 自带）
- [ ] "清屏"按钮 + 事件计数徽章
- [ ] WebServer 加静态资源：`cfg.staticFiles.add("/web", Location.CLASSPATH)`
- [ ] 测试：手动验收为主；curl 静态页断言 200 + 含关键字

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/resources/web/index.html | 全部前端 |
| 修改 | src/main/java/com/agentcli/web/WebServer.java | 静态资源 |

## UI 骨架参考

```html
<body>
  <header>AgentCli · <span id="dot"></span> · <span id="count">0</span> events
          <button onclick="clear()">clear</button></header>
  <main id="timeline"></main>
  <script>
    const es = new EventSource('/api/events');
    es.onmessage = e => { render(JSON.parse(e.data)); };
  </script>
</body>
```

- 深色主题打底（agent 工具默认审美），等宽字体显示工具参数
- tool_result 默认折叠，点击展开；事件多时只保留最近 200 条 DOM

## 验收标准

1. CLI 跑一个读文件任务，浏览器按顺序出现 蓝→橙→灰→绿 事件卡
2. 断开 CLI（Ctrl+C）后圆点变红，重启后 EventSource 自动重连恢复
3. 刷新页面不炸，历史事件不需要回放（接受清空）

## 收尾发布

```bash
git add -A && git commit -m "Day 8: web timeline UI (vanilla JS)" && git push
```

## 坑点提示

- EventSource 只支持 GET，断线自动重连是浏览器内建的，别自己写重试
- innerHTML 拼接前对所有 LLM/工具输出做 escapeHtml，防止内容里带标签
- 别上 React/Vue——单文件 index.html 是这个项目的卖点（简单到能读完）
