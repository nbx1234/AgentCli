package com.agentcli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * SSE 流解析：把 `data: {...}` 行转成增量文本。
 *
 * 独立成类便于单测（喂一段模拟文本流即可，不打网络）。
 */
public final class SseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseParser() {
    }

    /**
     * 解析一行 SSE。返回该行携带的增量文本；遇到空行 / 注释 / `data: [DONE]` /
     * 首帧（无 content）/ 无 choices 时返回 null，调用方应跳过。
     */
    public static String parseLine(String line) throws IOException {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }
        JsonNode root = MAPPER.readTree(data);
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode delta = choices.get(0).get("delta");
        if (delta == null || delta.get("content") == null || delta.get("content").isNull()) {
            return null;
        }
        return delta.get("content").asText();
    }

    /** 便捷方法：解析整段 SSE 文本并拼接所有增量（用于测试与静态内容解析）。 */
    public static String collectDeltas(String sseText) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (sseText == null) {
            return "";
        }
        for (String line : sseText.split("\\R")) {
            String delta = parseLine(line);
            if (delta != null) {
                sb.append(delta);
            }
        }
        return sb.toString();
    }
}