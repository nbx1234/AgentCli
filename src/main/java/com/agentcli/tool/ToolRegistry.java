package com.agentcli.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：按名字注册工具、查找、列出全部定义。
 *
 * 今天只做注册与查询（空壳），工具实现与执行在后续 Day 落地。
 */
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    /** 注册工具；同名覆盖。 */
    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    /** 按名字查找，未注册返回 null。 */
    public ToolDefinition lookup(String name) {
        return tools.get(name);
    }

    /** 列出全部已注册的工具定义。 */
    public List<ToolDefinition> listDefinitions() {
        return new ArrayList<>(tools.values());
    }
}