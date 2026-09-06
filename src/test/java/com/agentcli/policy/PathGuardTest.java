package com.agentcli.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathGuardTest {

    @TempDir
    File root;

    @Test
    void allowsInRootRelativePath() throws Exception {
        File f = new PathGuard(root).resolve("a/b.txt");
        // 用同样的 canonical 化方式计算期望值，规避 macOS 下 /var -> /private/var 符号链接的不一致
        assertEquals(new File(root, "a/b.txt").getCanonicalPath(), f.getPath());
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(SecurityException.class, () -> new PathGuard(root).resolve("../etc/passwd"));
        assertThrows(SecurityException.class, () -> new PathGuard(root).resolve("../../etc/passwd"));
    }

    @Test
    void rejectsAbsolutePathOutsideRoot() {
        assertThrows(SecurityException.class, () -> new PathGuard(root).resolve("/etc/passwd"));
    }

    @Test
    void rejectsRootPrefixLookalike() throws Exception {
        // 根目录的同名前缀目录不越界（create 一个子目录以验证仍被允许）
        File sub = new File(root, "rootx");
        Files.createDirectories(sub.toPath());
        File ok = new PathGuard(root).resolve("rootx");
        assertEquals(sub.getCanonicalPath(), ok.getPath());
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        // 在根目录内建软链接指向根外，读取该链接应被拒
        File outside = new File(root.getParentFile(), "outside-" + root.getName());
        Files.createDirectories(outside.toPath());
        File link = new File(root, "evil-link");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException e) {
            return; // 平台不支持则跳过
        }
        assertThrows(SecurityException.class, () -> new PathGuard(root).resolve("evil-link"));
        Files.delete(link.toPath());
        Files.delete(outside.toPath());
    }
}