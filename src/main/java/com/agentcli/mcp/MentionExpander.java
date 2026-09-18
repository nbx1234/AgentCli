package com.agentcli.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Day 17：`@server:uri` 提及展开器。
 *
 * 用户输入如 "总结 @fs:demo/mcp-fs/notes.md 的要点" 时，把命中注册资源的提及展开为
 * {@code <resource server="..." uri="...">内容</resource>} 内联块，再进 Agent。
 * 展开前的原始输入保留给 trace（trace 的 user 字段记原始输入，不记展开后）。
 */
public final class MentionExpander {

    /** @server:uri —— server 限 [A-Za-z0-9_-]，uri 到空白/行尾为止。 */
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_-]+):([^\\s<>]+)");

    /** 一个已解析的提及。 */
    public static final class Mention {
        public final String server;
        public final String uri;

        Mention(String server, String uri) {
            this.server = server;
            this.uri = uri;
        }
    }

    /** 从输入里找出所有提及（用于校验 server 是否已连接）。 */
    public static List<Mention> findMentions(String input) {
        List<Mention> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        Matcher m = MENTION.matcher(input);
        while (m.find()) {
            out.add(new Mention(m.group(1), m.group(2)));
        }
        return out;
    }

    /**
     * 展开：对每个提及，若 resolver 能提供内容则替换为 {@code <resource>} 块；
     * 返回 null 或空串的内容保持原样（不替换）。
     *
     * @param resolver (server, uri) → 资源内容；返回 null 表示未命中（保持原样）
     */
    public static String expand(String input, ResourceResolver resolver) {
        if (input == null || input.isBlank()) {
            return input;
        }
        Matcher m = MENTION.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String content = resolver.resolve(m.group(1), m.group(2));
            if (content == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String block = "<resource server=\"" + m.group(1) + "\" uri=\"" + m.group(2) + "\">\n"
                    + content + "\n</resource>";
            m.appendReplacement(sb, Matcher.quoteReplacement(block));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 资源解析回调。 */
    @FunctionalInterface
    public interface ResourceResolver {
        /** 返回资源内容；未命中返回 null。 */
        String resolve(String server, String uri);
    }
}