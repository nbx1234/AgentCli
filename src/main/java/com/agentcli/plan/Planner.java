package com.agentcli.plan;

import com.agentcli.llm.ChatClient;
import com.agentcli.llm.Message;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务规划器：一次性（非流式）调用 LLM，要求其只输出结构化 JSON 任务列表。
 *
 * JSON 解析失败（含围栏包裹、依赖引用缺失、空列表）时用补充约束重试 1 次。
 */
public final class Planner {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ChatClient client;
    private final ObjectMapper mapper;

    public Planner(ChatClient client) {
        this.client = client;
        this.mapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    /** 规划一个目标；extra 为补充要求（可为空）。解析失败自动重试 1 次。 */
    public ExecutionPlan plan(String goal, String extra) throws Exception {
        String raw1 = client.call(List.of(new Message("user", buildPrompt(goal, extra, null))));
        try {
            return parse(raw1);
        } catch (Exception ignore) {
            String raw2 = client.call(List.of(new Message("user", buildPrompt(goal, extra,
                    "你上次没有输出合法 JSON，请只输出 JSON，不要带任何说明文字。"))));
            return parse(raw2);
        }
    }

    private ExecutionPlan parse(String raw) throws Exception {
        JsonNode root = mapper.readTree(stripFence(raw));
        JsonNode tasksNode = root.get("tasks");
        if (tasksNode == null || !tasksNode.isArray()) {
            throw new IllegalArgumentException("输出缺少 tasks 数组");
        }
        List<Task> tasks = new ArrayList<>();
        for (JsonNode n : tasksNode) {
            tasks.add(mapper.treeToValue(n, Task.class));
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("空任务列表");
        }
        return new ExecutionPlan(tasks); // 依赖引用缺失时在此抛错，触达上层重试
    }

    /** 兼容 LLM 常带的 ```json ... ``` 围栏。 */
    private String stripFence(String s) {
        s = s.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n', 3);
            if (nl >= 0) {
                s = s.substring(nl + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        return s;
    }

    private String buildPrompt(String goal, String extra, String retryNote) {
        StringBuilder p = new StringBuilder()
                .append("你是任务规划器。把用户目标拆解为 2-6 个任务，输出严格 JSON：\n")
                .append("{\"tasks\":[{\"id\":\"t1\",\"title\":\"...\",\"prompt\":\"给执行者的完整指令\",\"dependsOn\":[]}]}\n")
                .append("不要输出 JSON 以外的任何文字。\n")
                .append("其中 prompt 字段要写成给执行者（另一个独立智能体）的自包含指令。\n")
                .append("用户目标：").append(goal);
        if (extra != null && !extra.isBlank()) {
            p.append("\n补充要求：").append(extra);
        }
        p.append("\n当前日期：").append(LocalDate.now(ZONE));
        if (retryNote != null) {
            p.append("\n").append(retryNote);
        }
        return p.toString();
    }
}