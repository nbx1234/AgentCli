package com.agentcli.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinToolsTest {

    @TempDir
    File root;

    @Test
    void readFileReadsExistingFile() throws Exception {
        Files.writeString(new File(root, "README.md").toPath(), "Hello World", StandardCharsets.UTF_8);
        ReadFileTool tool = new ReadFileTool(root);
        String result = tool.execute(Map.of("path", "README.md"));
        assertEquals("Hello World", result);
    }

    @Test
    void readFileRejectsEscape() {
        ReadFileTool tool = new ReadFileTool(root);
        String result = tool.execute(Map.of("path", "../../etc/passwd"));
        assertTrue(result.startsWith("拒绝"));
    }

    @Test
    void readFileRejectsBinary() throws Exception {
        byte[] bin = new byte[]{0x00, 0x01, 0x02};
        Files.write(new File(root, "bin.dat").toPath(), bin);
        ReadFileTool tool = new ReadFileTool(root);
        String result = tool.execute(Map.of("path", "bin.dat"));
        assertTrue(result.startsWith("拒绝"));
    }

    @Test
    void writeFileWritesWhenConfirmed() throws Exception {
        WriteFileTool tool = new WriteFileTool(root, new BufferedReader(new StringReader("y\n")));
        String result = tool.execute(Map.of("path", "test.txt", "content", "hello"));
        assertTrue(result.startsWith("已写入"));
        assertEquals("hello", Files.readString(new File(root, "test.txt").toPath()));
    }

    @Test
    void writeFileRejectsWhenDeclined() throws Exception {
        WriteFileTool tool = new WriteFileTool(root, new BufferedReader(new StringReader("n\n")));
        String result = tool.execute(Map.of("path", "test.txt", "content", "hello"));
        assertTrue(result.contains("拒绝"));
        assertFalse(new File(root, "test.txt").exists());
    }

    @Test
    void executeCommandRunsAndCapturesOutput() {
        ExecuteCommandTool tool = new ExecuteCommandTool(root);
        String result = tool.execute(Map.of("command", "echo hi"));
        assertTrue(result.contains("hi"));
    }

    @Test
    void executeCommandBlocksDangerousCommand() {
        ExecuteCommandTool tool = new ExecuteCommandTool(root);
        String result = tool.execute(Map.of("command", "rm -rf /"));
        assertTrue(result.contains("被拦截"));
    }
}