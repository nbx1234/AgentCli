package com.agentcli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 .env 加载器。
 *
 * 查找顺序：系统环境变量 → 项目根 `.env`（KEY=VALUE，忽略空行与 `#` 注释）。
 * 提供项目根目录定位，方便可执行 jar 运行时也能找到 .env。
 */
public final class Env {

    private static Map<String, String> fileValues;

    private Env() {
    }

    /** 获取环境变量，优先级：系统环境变量 > 项目根 .env。 */
    public static String get(String key) {
        String sys = System.getenv(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        return file().get(key);
    }

    /** 惰性加载 .env 并缓存。 */
    private static synchronized Map<String, String> file() {
        if (fileValues == null) {
            fileValues = loadFile();
        }
        return fileValues;
    }

    private static Map<String, String> loadFile() {
        Map<String, String> map = new HashMap<>();
        Path env = rootPath().resolve(".env");
        if (!Files.isRegularFile(env)) {
            return map;
        }
        try {
            List<String> lines = Files.readAllLines(env, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // 去掉可选的双引号包裹
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException e) {
            // .env 读取失败按空处理，不阻塞启动
        }
        return map;
    }

    /**
     * 定位项目根目录：优先 Maven 工作目录，其次按目录层级向上找含 pom.xml 的目录。
     */
    public static Path rootPath() {
        String userDir = System.getProperty("user.dir");
        Path cwd = Paths.get(userDir);
        if (Files.isRegularFile(cwd.resolve("pom.xml"))) {
            return cwd;
        }
        // 从 `.` 逐级向上找 pom.xml
        Path p = cwd.toAbsolutePath();
        while (p != null) {
            if (Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        return cwd;
    }
}