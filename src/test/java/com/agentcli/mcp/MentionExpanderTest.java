package com.agentcli.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionExpanderTest {

    private static final MentionExpander.ResourceResolver RESOLVE =
            (server, uri) -> "demo".equals(server) ? "内容[" + uri + "]" : null;

    @Test
    void expandsHitAndKeepsOriginalText() {
        String out = MentionExpander.expand("总结 @demo:a.md 的要点", RESOLVE);
        assertTrue(out.contains("<resource server=\"demo\" uri=\"a.md\">"));
        assertTrue(out.contains("内容[a.md]"));
        assertTrue(out.contains("</resource>"));
    }

    @Test
    void missKeepsMentionUntouched() {
        String in = "看看 @other:x 吧";
        assertEquals(in, MentionExpander.expand(in, RESOLVE));
    }

    @Test
    void noMentionReturnsInput() {
        String in = "普通问题，没有提及";
        assertEquals(in, MentionExpander.expand(in, RESOLVE));
    }

    @Test
    void nullInputSafe() {
        assertEquals(null, MentionExpander.expand(null, RESOLVE));
    }

    @Test
    void findMentionsParsesServerAndUri() {
        List<MentionExpander.Mention> ms = MentionExpander.findMentions("请读 @fs:notes.md 和 @git:repo/src");
        assertEquals(2, ms.size());
        assertEquals("fs", ms.get(0).server);
        assertEquals("notes.md", ms.get(0).uri);
        assertEquals("git", ms.get(1).server);
        assertEquals("repo/src", ms.get(1).uri);
    }

    @Test
    void multipleMentionsAllExpanded() {
        String out = MentionExpander.expand("@demo:x 与 @demo:y", RESOLVE);
        assertTrue(out.contains("内容[x]"));
        assertTrue(out.contains("内容[y]"));
    }
}