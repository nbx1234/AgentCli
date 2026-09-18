package com.agentcli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Day 16：MCP stdio 客户端。
 *
 * 以子进程方式启动外部 MCP server，通过 stdin/stdout 走<b>换行分隔</b> JSON-RPC 2.0
 * （不是 LSP 的 Content-Length 帧）。握手顺序：initialize → notifications/initialized →
 * tools/list → tools/call。id 自增，串行发送，按 id 配对响应。
 */
public final class McpClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverName;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final Thread stderrDrain;
    private int nextId = 1;

    /**
     * 启动子进程并完成 initialize 握手。
     *
     * @param name   server 名（仅日志/报错）
     * @param command 可执行文件
     * @param args    启动参数
     */
    public static McpClient start(String name, String command, java.util.List<String> args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(java.util.stream.Stream.concat(java.util.stream.Stream.of(command), args.stream()).toList());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        McpClient client = new McpClient(name, process);
        try {
            client.initialize();
            return client;
        } catch (IOException e) {
            client.close();
            throw e;
        }
    }

    private McpClient(String name, Process process) throws IOException {
        this.serverName = name;
        this.process = process;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        // stderr 必须及时消费，否则管道写满会把 server 卡死
        this.stderrDrain = new Thread(() -> {
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    System.out.println("[mcp:" + name + " stderr] " + line);
                }
            } catch (IOException ignored) {
            }
        }, "mcp-stderr-" + name);
        this.stderrDrain.setDaemon(true);
        this.stderrDrain.start();
    }

    /** initialize → notifications/initialized。失败抛 IOException。 */
    private void initialize() throws IOException {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.set("capabilities", MAPPER.createObjectNode());
        ObjectNode clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "agentcli");
        clientInfo.put("version", "0.0.1");
        params.set("clientInfo", clientInfo);

        int id = nextId++;
        JsonNode resp = request("initialize", params, id);
        JsonNode result = resp.path("result");
        if (result.isMissingNode()) {
            throw new IOException("initialize 失败: " + resp);
        }
        notify("notifications/initialized", null);
    }

    /** 列出 server 声明的工具（tools/list 的 result.tools 数组）。 */
    public JsonNode listTools() throws IOException {
        int id = nextId++;
        JsonNode resp = request("tools/list", null, id);
        JsonNode result = resp.path("result");
        if (result.isMissingNode()) {
            throw new IOException("tools/list 失败: " + resp);
        }
        return result.path("tools");
    }

    /** 列出 server 声明的 resources（resources/list 的 result.resources 数组）。不支持时返回空数组。 */
    public JsonNode listResources() throws IOException {
        int id = nextId++;
        JsonNode resp = request("resources/list", null, id);
        JsonNode result = resp.path("result");
        if (result.isMissingNode()) {
            return MAPPER.createArrayNode();
        }
        return result.path("resources");
    }

    /** 读取一个 resource（resources/read），返回 text 内容。 */
    public String readResource(String uri) throws IOException {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("uri", uri);
        int id = nextId++;
        JsonNode resp = request("resources/read", params, id);
        JsonNode error = resp.path("error");
        if (!error.isMissingNode()) {
            return "MCP resource 读取失败: " + error.path("message").asText(error.toString());
        }
        JsonNode contents = resp.path("result").path("contents");
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : contents) {
            sb.append(c.path("text").asText(""));
        }
        return sb.toString().isBlank() ? resp.toString() : sb.toString();
    }

    /** 调用工具，返回 result.content 中第一段 text 文本。 */
    public String callTool(String toolName, Map<String, Object> arguments) throws IOException {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        if (arguments == null || arguments.isEmpty()) {
            params.set("arguments", MAPPER.createObjectNode());
        } else {
            params.set("arguments", MAPPER.valueToTree(arguments));
        }
        int id = nextId++;
        JsonNode resp = request("tools/call", params, id);
        JsonNode error = resp.path("error");
        if (!error.isMissingNode()) {
            return "MCP 调用失败: " + error.path("message").asText(error.toString());
        }
        JsonNode content = resp.path("result").path("content");
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : content) {
            if ("text".equals(c.path("type").asText())) {
                sb.append(c.path("text").asText());
            }
        }
        return sb.toString().isBlank() ? resp.toString() : sb.toString();
    }

    /** 发送 JSON-RPC 请求并等该 id 的响应；期间丢弃无 id 的通知行。 */
    private JsonNode request(String method, JsonNode params, int id) throws IOException {
        write(encodeRequest(method, params, id));
        while (true) {
            String line = stdout.readLine();
            if (line == null) {
                throw new IOException("MCP server(" + serverName + ") 已退出（stdin 关闭）");
            }
            if (line.isBlank()) {
                continue;
            }
            JsonNode msg = MAPPER.readTree(line);
            if (msg.has("id") && msg.get("id").asInt(-1) == id) {
                return msg;
            }
            // 无 id 或 id 不匹配：通知/过期响应，跳过
        }
    }

    /** 发送无 id 的 JSON-RPC 通知。 */
    private void notify(String method, JsonNode params) throws IOException {
        write(encodeRequest(method, params, 0));
    }

    private void write(String line) throws IOException {
        stdin.write(line);
        stdin.write('\n');
        stdin.flush();
    }

    /** 编码一条 JSON-RPC 请求（id=0 表示通知，不带 id 字段）。 */
    public static String encodeRequest(String method, JsonNode params, int id) throws IOException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (id > 0) {
            node.put("id", id);
        }
        node.put("method", method);
        if (params != null) {
            node.set("params", params);
        }
        return MAPPER.writeValueAsString(node);
    }

    /** 解析响应行并校验 jsonrpc 版本；协议错误抛 IOException。 */
    public static JsonNode parseResponse(String line) throws IOException {
        JsonNode node = MAPPER.readTree(line);
        if (!"2.0".equals(node.path("jsonrpc").asText())) {
            throw new IOException("非 JSON-RPC 2.0 响应: " + line);
        }
        return node;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        try {
            process.destroy();
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}