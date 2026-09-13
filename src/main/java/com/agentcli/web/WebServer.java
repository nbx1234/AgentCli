package com.agentcli.web;

import com.agentcli.plan.ExecutionPlan;
import com.agentcli.plan.Task;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private volatile ExecutionPlan currentPlan;

    public WebServer(int port, WebSink sink) {
        this.port = port;
        this.sink = sink;
    }

    /** 设置当前计划，供 /api/dag 返回真实 DAG（可重复调用更新）。 */
    public void setCurrentPlan(ExecutionPlan plan) {
        this.currentPlan = plan;
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
        app.get("/api/dag", ctx -> ctx.json(dagJson()));
        app.sse("/api/events", client -> {
            sink.addClient(client);
            client.sendEvent("hello", "connected");
        });
        app.start(port);
    }

    /** 把当前计划序列化为 nodes/edges（前置依赖为空则返回空 DAG）。 */
    private Map<String, Object> dagJson() {
        ExecutionPlan plan = currentPlan;
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        if (plan != null) {
            for (Task t : plan.tasks()) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("id", t.id());
                n.put("title", t.title());
                n.put("status", t.status().name());
                nodes.add(n);
            }
            for (Task t : plan.tasks()) {
                for (String dep : t.dependsOn()) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("from", dep);
                    e.put("to", t.id());
                    edges.add(e);
                }
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nodes", nodes);
        root.put("edges", edges);
        return root;
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