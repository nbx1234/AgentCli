package com.agentcli.llm;

import com.agentcli.Env;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

/**
 * DeepSeek chat/completions 客户端。
 *
 * 用 HttpURLConnection + Jackson 实现单轮调用，
 * ObjectMapper 为静态单例。
 */
public class DeepSeekClient implements ChatClient {

    static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = defaultMapper();

    private static com.fasterxml.jackson.databind.ObjectMapper defaultMapper() {
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        m.configure(INDENT_OUTPUT, false);
        return m;
    }

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    /** 从 Env 读取 DEEPSEEK_API_KEY，缺失时抛出 IllegalStateException。 */
    public static DeepSeekClient fromEnv() {
        String key = Env.get("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("缺少 DEEPSEEK_API_KEY");
        }
        String base = Env.get("DEEPSEEK_BASE_URL");
        if (base == null || base.isBlank()) {
            base = "https://api.deepseek.com";
        }
        return new DeepSeekClient(key, base, "deepseek-chat");
    }

    public DeepSeekClient(String apiKey, String baseUrl, String model) {
        this(apiKey, baseUrl, model, 10_000, 120_000);
    }

    public DeepSeekClient(String apiKey, String baseUrl, String model,
                          int connectTimeoutMs, int readTimeoutMs) {
        this.apiKey = apiKey;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.model = model;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public String call(List<Message> messages) throws IOException {
        String body = buildRequestBody(messages);

        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/chat/completions")
                .toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return parseResponse(readStream(conn.getInputStream()));
            }
            // 4xx/5xx 不会抛异常，需读取错误体诊断
            throw new IOException("DeepSeek HTTP " + code + ": " + readStream(conn.getErrorStream()));
        } finally {
            conn.disconnect();
        }
    }

    /** 组装 body：{"model":..., "messages":[...], "stream":false}。 */
    String buildRequestBody(List<Message> messages) {
        String json = messages.stream()
                .map(m -> "{\"role\":\"" + m.role()
                        + "\",\"content\":\"" + escape(m.content()) + "\"}")
                .collect(Collectors.joining(","));
        return "{\"model\":\"" + model
                + "\",\"stream\":false,\"messages\":[" + json + "]}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** 解析响应，取 choices[0].message.content；判空规避 choices 为空或 finish_reason=length。 */
    String parseResponse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IOException("DeepSeek 响应缺少 choices");
        }
        JsonNode message = choices.get(0).get("message");
        if (message == null || message.get("content") == null || message.get("content").isNull()) {
            throw new IOException("DeepSeek 响应缺少 message.content");
        }
        return message.get("content").asText();
    }

    private static String readStream(InputStream in) throws IOException {
        try (InputStream is = in; java.util.Scanner sc = new java.util.Scanner(is, StandardCharsets.UTF_8)) {
            sc.useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        }
    }
}