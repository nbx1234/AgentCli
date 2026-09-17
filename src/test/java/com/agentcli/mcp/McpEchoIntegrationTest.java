package com.agentcli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day 16 真实联调：启动 examples/mcp-echo.js，走完整握手 + tools/list + tools/call。
 * 标记 @Tag("mcp")，无 node 或未配置时可跳过。
 */
@Tag("mcp")
class McpEchoIntegrationTest {

    @TempDir
    Path tmp;

    static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").start();
            boolean ok = p.waitFor() == 0;
            p.destroy();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("nodeAvailable")
    void echoServerHandshakeAndCallAdd() throws Exception {
        Path script = Path.of("examples", "mcp-echo.js").toAbsolutePath();
        assertTrue(Files.isRegularFile(script), "需要 examples/mcp-echo.js");

        try (McpClient client = McpClient.start("echo", "node", List.of(script.toString()))) {
            JsonNode tools = client.listTools();
            assertEquals(2, tools.size(), "echo server 应声明 2 个工具");

            String sum = client.callTool("add", Map.of("a", 3, "b", 5));
            assertEquals("8", sum);

            String echoed = client.callTool("echo", Map.of("text", "你好"));
            assertEquals("你好", echoed);
        }
    }
}