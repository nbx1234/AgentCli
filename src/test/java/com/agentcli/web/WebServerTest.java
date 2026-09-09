package com.agentcli.web;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerTest {

    @Test
    void healthReturns200() throws Exception {
        WebSink sink = new WebSink();
        WebServer server = new WebServer(0, sink);
        try {
            server.start();
            int port = server.getPort();
            assertTrue(port > 0);

            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + port + "/api/health").toURL().openConnection();
            int code = conn.getResponseCode();
            String body = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().reduce("", String::concat);
            assertEquals(200, code);
            assertTrue(body.contains("\"ok\""));
            assertTrue(body.contains("true"));
        } finally {
            server.stop();
        }
    }

    @Test
    void sseEndpointSendsHelloEvent() throws Exception {
        WebSink sink = new WebSink();
        WebServer server = new WebServer(0, sink);
        try {
            server.start();
            int port = server.getPort();

            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + port + "/api/events").toURL().openConnection();
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(4000);
            assertEquals(200, conn.getResponseCode());
            assertTrue(conn.getContentType().startsWith("text/event-stream"));

            String first = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)).readLine();
            assertTrue(first != null && (first.contains("hello") || first.contains("connected") || first.contains("event:")));
            conn.disconnect();
        } finally {
            server.stop();
        }
    }

    @Test
    void servesIndexPage() throws Exception {
        WebSink sink = new WebSink();
        WebServer server = new WebServer(0, sink);
        try {
            server.start();
            int port = server.getPort();

            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://127.0.0.1:" + port + "/").toURL().openConnection();
            assertEquals(200, conn.getResponseCode());
            String body = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().reduce("", String::concat);
            assertTrue(body.contains("AgentCli"));
            assertTrue(body.contains("EventSource"));
            conn.disconnect();
        } finally {
            server.stop();
        }
    }
}