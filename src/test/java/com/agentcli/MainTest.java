package com.agentcli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void versionStringIsStable() {
        assertEquals("0.0.1", Main.versionForTest());
    }

    @Test
    void bannerContainsName() {
        assertTrue(Main.bannerForTest().contains("Agent"),
                "Banner should mention Agent");
    }
}
