package com.agentcli.mcp;

import com.agentcli.Env;
import com.agentcli.tool.ToolDefinition;
import com.agentcli.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Day 16：MCP server 管理器。
 *
 * 读取 {@code ~/.agentcli/mcp.json}（可用环境变量 {@code AGENTCLI_MCP_CONFIG} 覆盖）：
 * <pre>
 * {"mcpServers":{"fs":{"command":"npx","args":["-y","@modelcontextprotocol/server-filesystem","/tmp/mcp-demo"]}}}
 * </pre>
 * 对每个 server 启动 {@link McpClient}，把其 tools/list 声明的工具以
 * {@code mcp__<server>__<tool>} 的名字注册进共享 {@link ToolRegistry}——生态大门打开。
 */
public final class McpServerManager {

    /** 名称前缀：mcp__<server>__<tool>。 */
    static String toolName(String server, String tool) {
        return "mcp__" + server + "__" + tool;
    }

    /** 资源虚拟工具名：mcp__<server>__resource__<uri>。uri 非字母数字时做占位转义。 */
    static String resourceToolName(String server, String uri) {
        String safe = uri.replaceAll("[^A-Za-z0-9_\\-]", "_");
        if (safe.length() > 60) {
            safe = safe.substring(0, 60);
        }
        return "mcp__" + server + "__resource__" + safe;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path configPath;
    private final List<Connection> connections = new ArrayList<>();

    /** 一个已连接的 server：客户端 + 注册进注册表的工具数。 */
    public static final class Connection {
        public final String server;
        public final McpClient client;
        public final int toolCount;

        Connection(String server, McpClient client, int toolCount) {
            this.server = server;
            this.client = client;
            this.toolCount = toolCount;
        }
    }

    public McpServerManager() {
        String cfg = Env.get("AGENTCLI_MCP_CONFIG");
        if (cfg != null && !cfg.isBlank()) {
            this.configPath = Path.of(cfg);
        } else {
            this.configPath = Path.of(System.getProperty("user.home"), ".agentcli", "mcp.json");
        }
    }

    public Path configPath() {
        return configPath;
    }

    /** 已连接的 servers（供 /mcp 展示）。 */
    public List<Connection> connections() {
        return connections;
    }

    /** server 是否已连接。 */
    public boolean isConnected(String server) {
        return find(server) != null;
    }

    /**
     * Day 17：按 @server:uri 读取资源内容。server 未连接或读取失败返回 null
     * （调用方保持提及原样）。资源内容即 filesystem server 的文本。
     */
    public String resolveResource(String server, String uri) {
        Connection c = find(server);
        if (c == null) {
            return null;
        }
        try {
            return c.client.readResource(uri);
        } catch (IOException e) {
            return null;
        }
    }

    private Connection find(String server) {
        for (Connection c : connections) {
            if (c.server.equals(server)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 读取配置并连接所有 server，把工具注册进 registry。
     *
     * @return 成功连接的 server 名列表
     * @throws IOException 配置文件缺失/损坏
     */
    public List<String> connectAll(ToolRegistry registry) throws IOException {
        connections.clear();
        if (!Files.isRegularFile(configPath)) {
            throw new IOException("未找到 MCP 配置 " + configPath + "（可写 AGENTCLI_MCP_CONFIG 覆盖）");
        }
        JsonNode root = MAPPER.readTree(Files.readString(configPath, StandardCharsets.UTF_8));
        JsonNode servers = root.path("mcpServers");
        if (!servers.isObject() || servers.isEmpty()) {
            throw new IOException("mcp.json 缺少 mcpServers 对象");
        }
        List<String> ok = new ArrayList<>();
        var it = servers.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String name = e.getKey();
            String command = e.getValue().path("command").asText("");
            List<String> args = new ArrayList<>();
            for (JsonNode a : e.getValue().path("args")) {
                args.add(a.asText());
            }
            if (command.isBlank()) {
                System.out.println("[mcp] 跳过 " + name + "：缺少 command");
                continue;
            }
            try {
                McpClient client = McpClient.start(name, command, args);
                JsonNode tools = client.listTools();
                int n = 0;
                for (JsonNode t : tools) {
                    String tool = t.path("name").asText("");
                    String desc = t.path("description").asText("MCP 工具 " + name + "/" + tool);
                    JsonNode schema = t.path("inputSchema");
                    String paramsJson = schema.isMissingNode() || schema.isNull()
                            ? "{\"type\":\"object\",\"properties\":{}}"
                            : schema.toString();
                    String fullName = toolName(name, tool);
                    String toolDesc = desc + "（MCP server: " + name + "）";
                    registry.register(new ToolDefinition(fullName, toolDesc, paramsJson),
                            argsMap -> {
                                try {
                                    return client.callTool(tool, argsMap);
                                } catch (IOException ex) {
                                    return "MCP 调用失败(" + name + "/" + tool + "): " + ex.getMessage();
                                }
                            });
                    n++;
                }
                // Day 17：resources/list 是"可选能力"，把每个资源注册成虚拟只读工具 mcp__<s>__resource__<uri>
                try {
                    JsonNode resources = client.listResources();
                    for (JsonNode r : resources) {
                        String uri = r.path("uri").asText("");
                        if (uri.isBlank()) {
                            continue;
                        }
                        String label = r.path("name").asText(uri);
                        String fullName = resourceToolName(name, uri);
                        registry.register(new ToolDefinition(fullName,
                                        "读取 MCP 资源 " + label + "（server: " + name + "）",
                                        "{\"type\":\"object\",\"properties\":{}}"),
                                argsMap -> {
                                    try {
                                        return client.readResource(uri);
                                    } catch (IOException ex) {
                                        return "MCP resource 读取失败(" + name + "/" + uri + "): " + ex.getMessage();
                                    }
                                });
                        n++;
                    }
                } catch (IOException ignore) {
                    // server 不支持 resources/list，属正常，不作为失败
                }
                connections.add(new Connection(name, client, n));
                ok.add(name);
                System.out.println("[mcp] 已连接 " + name + "：" + n + " 个工具/资源");
            } catch (IOException ex) {
                System.out.println("[mcp] 连接 " + name + " 失败: " + ex.getMessage());
            }
        }
        return ok;
    }

    /** 关闭所有已连接 server。 */
    public void closeAll() {
        for (Connection c : connections) {
            try {
                c.client.close();
            } catch (RuntimeException ignore) {
            }
        }
        connections.clear();
    }

    /** 汇总状态文本（供 /mcp 打印）。 */
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Connection c : connections) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alive", c.client.isAlive());
            m.put("tools", c.toolCount);
            out.put(c.server, m);
        }
        return out;
    }
}