package com.agentcli.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划里的一个任务节点。
 *
 * 由 Planner 的 JSON 反序列化而来（字段级可见，Jackson 直接填）、
 * 后续由执行阶段推进状态机并写入结论。
 */
public final class Task {

    /** 任务状态机：PENDING → RUNNING → DONE / FAILED；FAILED 时下游被标 SKIPPED。 */
    public enum Status { PENDING, RUNNING, DONE, FAILED, SKIPPED }

    private String id;
    private String title;
    private String prompt;
    private List<String> dependsOn = new ArrayList<>();

    private Status status = Status.PENDING;
    private String conclusion; // 执行结论摘要：工序间传递的产出

    public Task() {
    }

    public Task(String id, String title, String prompt, List<String> dependsOn) {
        this.id = id;
        this.title = title;
        this.prompt = prompt;
        this.dependsOn = dependsOn == null ? new ArrayList<>() : dependsOn;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String prompt() {
        return prompt;
    }

    public List<String> dependsOn() {
        return dependsOn;
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String conclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    /** 给 Jackson 从 JSON 填 id。 */
    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn == null ? new ArrayList<>() : dependsOn;
    }
}