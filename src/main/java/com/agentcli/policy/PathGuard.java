package com.agentcli.policy;

import java.io.File;
import java.io.IOException;

/**
 * 路径护栏：解析用户传入的路径，强制限制在项目根目录内。
 *
 * 用 canonical path（解析符号链接与 . / .. ），并带 {@code File.separator} 边界判断，
 * 防止 `../` 逃逸与 `/{root之外的绝对路径}` 越过根目录。
 */
public final class PathGuard {

    private final File root;

    public PathGuard(File root) {
        this.root = root;
    }

    /** 当前项目根目录（canonical）。 */
    public File root() throws IOException {
        return root.getCanonicalFile();
    }

    /**
     * 校验并把用户路径解析到根目录内的 canonical 绝对路径。
     *
     * @return 允许访问的规范路径
     * @throws IOException        路径解析失败
     * @throws SecurityException  解析结果越出根目录
     */
    public File resolve(String userPath) throws IOException {
        if (userPath == null) {
            throw new SecurityException("空路径");
        }
        // 拒绝用户直接给绝对路径：避免歧义，统一用相对项目根解析
        if (new File(userPath).isAbsolute()) {
            throw new SecurityException("路径越界被拒绝: " + userPath);
        }
        File rootCanonical = root.getCanonicalFile();
        File candidate = new File(rootCanonical, userPath).getCanonicalFile();
        String rootPath = rootCanonical.getPath();
        String candPath = candidate.getPath();
        if (!candPath.equals(rootPath) && !candPath.startsWith(rootPath + File.separator)) {
            throw new SecurityException("路径越界被拒绝: " + userPath);
        }
        return candidate;
    }
}