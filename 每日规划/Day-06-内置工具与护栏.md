# Day 6 · 三个内置工具 + 安全护栏

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

read_file / write_file / execute_command 三个真工具上线，路径和命令有基础护栏。

## 任务清单

- [ ] `tool/ReadFileTool.java`：相对路径解析到项目根，超长截断，二进制检测
- [ ] `tool/WriteFileTool.java`：写入前 CLI 里 y/n 确认（今天先硬编码在工具里，Day 18 抽成策略）
- [ ] `tool/ExecuteCommandTool.java`：`ProcessBuilder` + bash，超时 60s，输出截断
- [ ] 新增 `policy/PathGuard.java`：canonical path 必须在项目根内，防 `../` 逃逸和符号链接
- [ ] 新增 `policy/CommandGuard.java`：黑名单（rm -rf /、sudo、shutdown、mkfs、> /dev/sda 等）
- [ ] 测试：PathGuard 逃逸用例 5 个、CommandGuard 黑名单用例 5 个

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/tool/ReadFileTool.java | |
| 新增 | src/main/java/com/agentcli/tool/WriteFileTool.java | |
| 新增 | src/main/java/com/agentcli/tool/ExecuteCommandTool.java | |
| 新增 | src/main/java/com/agentcli/policy/PathGuard.java | 路径护栏 |
| 新增 | src/main/java/com/agentcli/policy/CommandGuard.java | 命令护栏 |
| 新增 | src/test/java/com/agentcli/policy/PathGuardTest.java 等 | 护栏测试 |
| 修改 | Agent.java system prompt | 注入 3 工具使用说明 |

## 验收标准

1. "读一下 README 第一段" → Agent 调 read_file 并总结
2. "创建 test.txt 内容 hello" → 弹 y/n 确认，同意后文件出现；拒绝时 Agent 收到"用户拒绝"并改口
3. 读 `../../etc/passwd` 被 PathGuard 拒绝，Agent 收到拒绝消息而不是文件内容
4. `rm -rf /` 被 CommandGuard 拦截

## 收尾发布

```bash
git add -A && git commit -m "Day 6: built-in tools + path/command guards" && git push
```

## 坑点提示

- PathGuard 用 `File.getCanonicalPath()` 而不是字符串 startsWith 原始路径（符号链接会骗过后者）
- execute_command 必须 merge stderr，工作目录 = 项目根
- Windows 兼容先不管（你只有 macOS），但代码别写死 `/bin/bash` 之外的路径假设
- 工具 description 写得好坏直接影响 LLM 会不会用——每个写 2-3 句，说清参数和返回格式
