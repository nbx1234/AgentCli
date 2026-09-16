package com.agentcli.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTemplateTest {

    @TempDir
    Path tmp;

    private static final String TPL = "---\n"
            + "name: summarize-readme\n"
            + "goal: 阅读指定文件并生成摘要文件\n"
            + "params: [source, output]\n"
            + "---\n"
            + "## 步骤\n"
            + "1. read_file {{source}}\n"
            + "2. 根据内容写 {{output}}，格式：一句话定位 + 3-5 条要点\n";

    @Test
    void parseExtractsFrontMatter() {
        SkillRegistry.Skill s = SkillRegistry.parse(TPL);
        assertEquals("summarize-readme", s.name);
        assertEquals("阅读指定文件并生成摘要文件", s.goal);
        assertEquals(java.util.List.of("source", "output"), s.params);
        assertTrue(s.body.contains("{{source}}"));
    }

    @Test
    void renderSubstitutesAllParams() throws Exception {
        SkillRegistry.Skill s = SkillRegistry.parse(TPL);
        SkillRegistry reg = new SkillRegistry(tmp);
        String out = reg.render(s, Map.of("source", "README.md", "output", "summary.txt"));
        assertTrue(out.contains("read_file README.md"));
        assertTrue(out.contains("写 summary.txt"));
        assertTrue(!out.contains("{{"));
    }

    @Test
    void renderMissingParamListsAvailableOnes() throws Exception {
        SkillRegistry.Skill s = SkillRegistry.parse(TPL);
        SkillRegistry reg = new SkillRegistry(tmp);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> reg.render(s, Map.of("source", "README.md")));
        assertTrue(e.getMessage().contains("output"));
        assertTrue(e.getMessage().contains("source"));
    }

    @Test
    void renderUnknownParamRejected() throws Exception {
        SkillRegistry.Skill s = SkillRegistry.parse(TPL);
        SkillRegistry reg = new SkillRegistry(tmp);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> reg.render(s, Map.of("source", "a", "output", "b", "extra", "c")));
        assertTrue(e.getMessage().contains("extra"));
    }

    @Test
    void saveThenLoadRoundTrip() throws Exception {
        SkillRegistry reg = new SkillRegistry(tmp);
        SkillRegistry.Skill s = SkillRegistry.parse(TPL);
        reg.save(s);

        SkillRegistry reg2 = new SkillRegistry(tmp);
        SkillRegistry.Skill loaded = reg2.load("summarize-readme");
        assertEquals("summarize-readme", loaded.name);
        assertEquals(2, loaded.params.size());
        assertTrue(Files.exists(tmp.resolve("summarize-readme.md")));
    }

    @Test
    void loadUnknownThrowsWithHint() throws Exception {
        SkillRegistry reg = new SkillRegistry(tmp);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> reg.load("nope"));
        assertTrue(e.getMessage().contains("nope"));
        assertTrue(e.getMessage().contains("list"));
    }

    @Test
    void saveRejectsBadName() throws Exception {
        SkillRegistry reg = new SkillRegistry(tmp);
        SkillRegistry.Skill bad = new SkillRegistry.Skill("has space", "g", java.util.List.of("x"), "body");
        assertThrows(IllegalArgumentException.class, () -> reg.save(bad));
    }

    @Test
    void parseRejectsBrokenFrontMatter() {
        assertThrows(IllegalArgumentException.class, () -> SkillRegistry.parse("no front matter"));
        assertThrows(IllegalArgumentException.class, () -> SkillRegistry.parse("---\nname: x\n"));
        assertThrows(IllegalArgumentException.class, () -> SkillRegistry.parse("---\ngoal: g\n---\nbody"));
    }

    @Test
    void defaultDirIsUnderUserHome() {
        assertTrue(SkillRegistry.skillsDir().toString().contains(System.getProperty("user.home")));
    }
}