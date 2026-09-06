package com.agentcli.policy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 命令护栏：黑名单拦截危险命令，避免 Agent 申请执行破坏性操作。
 *
 * 基于小写归一后的命令文本做特征匹配。只挡最危险的，正常 shell 命令放行。
 */
public final class CommandGuard {

    private static final List<String> BLACKLIST_SUBSTRINGS = List.of(
            ":(){ :|:& };:",
            "rm -rf /",
            "rm -fr /",
            "rmdir",
            "shutdown",
            "reboot",
            "mkfs",
            "fdisk",
            "dd of=/dev/sd",
            "dd if=/dev/zero of=/dev/",
            "> /dev/sd",
            "chown -r / ",
            "chmod -r 7 / ",
            "sudo ",
            "su -"
    );

    private static final List<Pattern> BLACKLIST_PATTERNS = List.of(
            Pattern.compile("curl\\s+\\S.*\\|\\s*sh\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("wget\\s+\\S.*\\|\\s*sh\\b", Pattern.CASE_INSENSITIVE)
    );

    private CommandGuard() {
    }

    /**
     * 校验命令是否安全。
     *
     * @throws SecurityException 命中黑名单
     */
    public static void check(String command) {
        if (command == null || command.isBlank()) {
            throw new SecurityException("空命令");
        }
        String lower = command.toLowerCase(Locale.ROOT).replace('\n', ' ').trim();
        for (String bad : BLACKLIST_SUBSTRINGS) {
            if (lower.contains(bad)) {
                throw new SecurityException("命令被护栏拦截: " + command.trim());
            }
        }
        for (Pattern p : BLACKLIST_PATTERNS) {
            if (p.matcher(lower).find()) {
                throw new SecurityException("命令被护栏拦截: " + command.trim());
            }
        }
    }
}