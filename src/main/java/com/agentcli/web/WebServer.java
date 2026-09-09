package com.agentcli.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;

/**
 * 本地 Web 服务：提供健康检查、静态前端页与 SSE 事件流，让浏览器订阅 Agent 过程事件。
 *
 * 端口由构造参数给定（0 表示随机端口，便于测试/env 配置后使用）。
 */
public class WebServer {

    private final int port;
    private final WebSink sink;
    private Javalin app;

    public WebServer(int port, WebSink sink) {
        this.port = port;
        this.sink = sink;
    }

    /** 启动服务；端口被占时抛异常，由调用方捕获提示。 */
    public void start() {
        app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            // 前端单页：把 classpath 下 /web 托管到站点根，根路径自动命中 index.html
            cfg.staticFiles.add(sc -> {
                sc.hostedPath = "/";
                sc.directory = "/web";
                sc.location = Location.CLASSPATH;
            });
        });
        app.get("/api/health", ctx -> ctx.json(Map.of("ok", true)));
        app.sse("/api/events", client -> {
            sink.addClient(client);
            client.sendEvent("hello", "connected");
        });
        app.start(port);
    }

    /** 启动后实际绑定的端口（port=0 时用于取随机端口）。 */
    public int getPort() {
        return app == null ? port : app.port();
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }
}