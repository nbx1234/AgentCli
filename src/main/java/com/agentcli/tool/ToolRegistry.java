package com.agentcli.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 工具注册表：注册 schema + 可执行处理器，支持按名查找、列出定义、执行一次调用。
 *
 * 参数解析失败或执行抛异常时，都会转成字符串结果返回，交给 LLM 自行调整，不直接崩。
 */
public class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Function<Map<String, Object>, String>> executors = new LinkedHashMap<>();

    /** 仅注册定义（无执行器），用于 lookup / listDefinitions。 */
    public void register(ToolDefinition tool) {
        register(tool, null);
    }

    /** 注册可执行工具：schema + 参数处理器。 */
    public void register(ToolDefinition tool, Function<Map<String, Object>, String> executor) {
        definitions.put(tool.name(), tool);
        if (executor != null) {
            executors.put(tool.name(), executor);
        }
    }

    /** 按名字查找定义，未注册返回 null。 */
    public ToolDefinition lookup(String name) {
        return definitions.get(name);
    }

    /** 列出全部已注册的工具定义。 */
    public List<ToolDefinition> listDefinitions() {
        return new ArrayList<>(definitions.values());
    }

    /** 执行一次工具调用，返回字符串结果。 */
    public String execute(ToolCall tc) {
        Function<Map<String, Object>, String> fn = executors.get(tc.name());
        if (fn == null) {
            return "未知工具: " + tc.name();
        }
        try {
            Map<String, Object> args = (tc.argumentsJson() == null || tc.argumentsJson().isBlank())
                    ? Map.of()
                    : MAPPER.readValue(tc.argumentsJson(), new TypeReference<Map<String, Object>>() {});
            return fn.apply(args);
        } catch (Exception e) {
            return "工具执行异常: " + e.getMessage();
        }
    }
}