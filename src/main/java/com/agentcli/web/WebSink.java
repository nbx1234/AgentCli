package com.agentcli.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.sse.SseClient;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 广播：把事件推给所有已连接的浏览器 SSE 客户端。
 *
 * 持有 CopyOnWriteArrayList，客户端断开时通过 onClose 移除，避免对死连接发事件。
 */
public class WebSink implements EventEmitter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CopyOnWriteArrayList<SseClient> clients = new CopyOnWriteArrayList<>();

    /** 新客户端连接（由 WebServer 在 SSE handler 里调用）。 */
    public void addClient(SseClient client) {
        clients.add(client);
        client.onClose(() -> clients.remove(client));
    }

    /** 当前在线客户端数（测试用）。 */
    public int clientCount() {
        return clients.size();
    }

    @Override
    public void emit(String type, Map<String, Object> payload) {
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return; // 序列化失败丢弃事件，不影响主流程
        }
        for (SseClient client : clients) {
            try {
                if (!client.terminated()) {
                    client.sendEvent(type, json);
                }
            } catch (Exception e) {
                clients.remove(client);
            }
        }
    }
}