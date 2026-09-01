# Day 15 · /skill save 录制即技能

> 状态：⬜ 未开始　|　实际日期：________　|　commit：________

## 目标

把一段 trace 抽象成参数化技能模板，`/skill run` 复用——"做过一次 = 会做一类"。

## 任务清单

- [ ] 新增 `skill/SkillRegistry.java`：技能目录 `~/.agentcli/skills/*.md`
- [ ] `/skill save <name> <traceId>`：LLM 非流式调用分析 trace，产出模板：
  ```markdown
  ---
  name: summarize-readme
  goal: 阅读指定文件并生成摘要文件
  params: [source, output]
  ---
  ## 步骤
  1. read_file {{source}}
  2. 根据内容写 {{output}}，格式：一句话定位 + 3-5 条要点
  ```
- [ ] 参数抽取规则：出现 ≥1 次的路径/URL/文件名候选参数化，LLM 给出语义化参数名
- [ ] `/skill list` / `/skill show <name>` / `/skill run <name> k1=v1 k2=v2`：模板变量替换后作为 user prompt 前置注入，走普通 Agent 执行
- [ ] 执行技能的轮次同样录 trace（meta 标记 skill=name）
- [ ] 测试：模板变量替换；非法参数名报错列出可用 params

## 涉及文件

| 操作 | 路径 | 说明 |
|---|---|---|
| 新增 | src/main/java/com/agentcli/skill/SkillRegistry.java | |
| 修改 | src/main/java/com/agentcli/Main.java | /skill 命令组 |
| 修改 | src/main/java/com/agentcli/prompt/SystemPrompt.java | 已有技能索引注入（名称+参数，≤10 个） |
| 新增 | src/test/java/com/agentcli/skill/SkillTemplateTest.java | |

## 验收标准

1. 用 Day 13 录的"读 README 生成 summary"trace 存成 skill，对另一个 md 文件 `/skill run` 成功产出对应摘要
2. system prompt 里能看到技能索引（LLM 会主动建议"要不要跑 summarize-readme 这个技能"）
3. 参数缺省时列出该技能需要的参数，不瞎跑

## 收尾发布

```bash
git add -A && git commit -m "Day 15: /skill save + run (record-as-skill)" && git push
```

## 坑点提示

- 模板里只参数化"输入类"值（路径/URL），步骤逻辑不参数化——逻辑变化应该重新录制
- LLM 抽参数可能抽错（把临时文件名当参数），save 时展示模板让用户确认 y/n 再落盘
- 至此差异点②闭环，录 30 秒 demo GIF，Day 20 README 的头图素材
