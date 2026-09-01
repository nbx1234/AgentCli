# Day 18 · HITL 审批策略 + 审计日志

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

把 Day 6 的硬编码确认升级成策略层：只读放行、写/执行必审、全量审计。安全线立住。

## 任务清单

- [ ] 新增 `hitl/ApprovalPolicy.java`：`enum Decision {ALLOW, ASK, DENY}` + 按工具分派：
  - read_file / mcp 只读工具 → ALLOW
  - write_file / execute_command / 其他 mcp → ASK
  - 命中 CommandGuard 黑名单 → DENY（用户确认也无效）
- [ ] 新增 `hitl/ApprovalRequest.java`：工具名 / 参数摘要 / 风险说明（execute 显示完整命令）
- [ ] CLI 审批面板：`⚠ execute_command: rm -rf build/  [y/N]`，默认拒绝；ask 记忆选项 `a`=本会话允许该工具
- [ ] Web 事件流加 `approval_request` / `approval_result` 事件（时间线可见被拦截的调用）
- [ ] 新增 `policy/AuditLog.java`：`~/.agentcli/audit.jsonl` 追加：ts / tool / args / decision / 来源（user|policy）
- [ ] `/audit` 命令：最近 20 条
- [ ] 测试：策略矩阵全覆盖；DENY 后 execute 不被调用；audit 落盘断言

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/hitl/ApprovalPolicy.java | |
| 新增 | src/main/java/com/agentcli/hitl/ApprovalRequest.java | |
| 新增 | src/main/java/com/agentcli/policy/AuditLog.java | |
| 修改 | Agent.java | 工具执行前过策略 |
| 修改 | Web 前端 | 审批事件渲染 |
| 新增 | src/test/java/com/agentcli/hitl/ApprovalPolicyTest.java | |

## 策略矩阵（写进代码注释和 README）

| 工具 | 决策 |
|---|---|
| read_file | ALLOW |
| write_file | ASK |
| execute_command | ASK + CommandGuard 可 DENY |
| mcp 只读（list/read 类名） | ALLOW |
| 其他 mcp | ASK |
| CommandGuard 黑名单 | DENY |

## 验收标准

1. 纯读任务全程零打断；写文件任务弹确认，拒绝后 Agent 收到拒绝并改方案
2. `rm -rf /` 直接 DENY，日志有记录，无 y/n 弹窗
3. `/audit` 能查到刚才每次决策；会话内 `a` 后同类工具不再打断

## 收尾发布

```bash
git add -A && git commit -m "Day 18: HITL approval policy + audit log" && git push
```

## 坑点提示

- ALLOW 判定 mcp 工具用**名称启发式**（list/get/read/search 开头），说明文档里承认这是启发式不是保证
- audit.jsonl 只追加不修改，测试用临时目录注入路径
- replay --apply 与审批层共用同一策略——回放复用 Day 14 的逐步确认，不绕过
