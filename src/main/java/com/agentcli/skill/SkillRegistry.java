package com.agentcli.skill;

import com.agentcli.Env;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Day 15：技能目录管理——"录制即技能"。
 *
 * 技能是一个带 front-matter 的 Markdown 模板，存于 {@code ~/.agentcli/skills/*.md}：
 * <pre>
 * ---
 * name: summarize-readme
 * goal: 阅读指定文件并生成摘要文件
 * params: [source, output]
 * ---
 * ## 步骤
 * 1. read_file {{source}}
 * 2. 根据内容写 {{output}}，格式：一句话定位 + 3-5 条要点
 * </pre>
 * 只参数化"输入类"值（路径/URL），步骤逻辑不参数化——逻辑变化应重新录制。
 */
public final class SkillRegistry {

    /** front-matter 分隔。 */
    static final String FM = "---";

    private static final Pattern PARAM = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
    private static final Pattern PARAMS_LIST = Pattern.compile("params:\\s*\\[([^\\]]*)]");

    private final Path dir;

    public SkillRegistry() throws IOException {
        this(skillsDir());
    }

    public SkillRegistry(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
    }

    public static Path skillsDir() {
        String d = Env.get("AGENTCLI_SKILL_DIR");
        if (d != null && !d.isBlank()) {
            return Path.of(d);
        }
        return Path.of(System.getProperty("user.home"), ".agentcli", "skills");
    }

    /** 一份已解析的技能模板。 */
    public static final class Skill {
        public final String name;
        public final String goal;
        public final List<String> params;
        public final String body;

        Skill(String name, String goal, List<String> params, String body) {
            this.name = name;
            this.goal = goal;
            this.params = params;
            this.body = body;
        }
    }

    /** 写入技能文件（覆盖已存在同名项）；返回写入的文件路径。 */
    public Path save(Skill skill) throws IOException {
        if (!skill.name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("技能名只能含字母数字下划线中划线，最长 64 字符");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(FM).append('\n');
        sb.append("name: ").append(skill.name).append('\n');
        sb.append("goal: ").append(skill.goal).append('\n');
        sb.append("params: [").append(String.join(", ", skill.params)).append("]\n");
        sb.append(FM).append('\n');
        sb.append(skill.body).append('\n');
        Path file = dir.resolve(skill.name + ".md");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    /** 列出全部技能（按名）。 */
    public List<Skill> list() throws IOException {
        List<Skill> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var s = Files.list(dir)) {
            for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".md")).sorted().toList()) {
                try {
                    out.add(parse(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IllegalArgumentException ignore) {
                    // 跳过损坏的技能文件
                }
            }
        }
        return out;
    }

    /** 按名加载技能，未找到抛 IllegalArgumentException。 */
    public Skill load(String name) throws IOException {
        Path file = dir.resolve(name + ".md");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("没有这个技能: " + name + "（可用 /skill list 查看）");
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** 渲染：把 {{param}} 替换为用户提供的值；缺参或未知参数报错。 */
    public String render(Skill skill, java.util.Map<String, String> values) {
        List<String> missing = new ArrayList<>();
        for (String p : skill.params) {
            if (values == null || !values.containsKey(p)) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少参数 " + String.join(", ", missing)
                    + "；可用参数: " + String.join(", ", skill.params));
        }
        for (String k : values.keySet()) {
            if (!skill.params.contains(k)) {
                throw new IllegalArgumentException("未知参数 " + k + "；可用参数: " + String.join(", ", skill.params));
            }
        }
        Matcher m = PARAM.matcher(skill.body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(values.get(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 从技能文件内容解析出模板。front-matter 缺失或字段不全抛错。 */
    public static Skill parse(String raw) {
        int first = raw.indexOf(FM);
        if (first != 0) {
            throw new IllegalArgumentException("技能文件缺少 front-matter");
        }
        int second = raw.indexOf(FM, first + FM.length());
        if (second < 0) {
            throw new IllegalArgumentException("技能文件 front-matter 未闭合");
        }
        String fm = raw.substring(first + FM.length(), second);
        String body = raw.substring(second + FM.length()).stripLeading();

        String name = field(fm, "name");
        String goal = field(fm, "goal");
        List<String> params = new ArrayList<>();
        Matcher pm = PARAMS_LIST.matcher(fm);
        if (pm.find()) {
            for (String p : pm.group(1).split(",")) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    params.add(t);
                }
            }
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("技能缺少 name");
        }
        return new Skill(name, goal == null ? "" : goal, params, body);
    }

    private static String field(String fm, String key) {
        for (String line : fm.split("\n")) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                return t.substring(key.length() + 1).trim();
            }
        }
        return null;
    }
}