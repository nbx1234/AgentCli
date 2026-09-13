package com.agentcli.plan;

/**
 * 计划审阅输入的解析：把用户按键/短语归一成三种含义之一。
 * 非法输入返回 null，由调用方友好重问。
 */
public final class PlanReviewParser {

    public enum Choice { RUN, REFINE, CANCEL }

    private PlanReviewParser() {
    }

    /** 解析 r/i/c（或 run/refine/cancel，不区分大小写与空白）；非法返回 null。 */
    public static Choice parseChoice(String input) {
        if (input == null) {
            return Choice.CANCEL; // EOF 视为取消
        }
        String s = input.trim().toLowerCase();
        if (s.equals("r") || s.equals("run") || s.equals("执行")) {
            return Choice.RUN;
        }
        if (s.equals("i") || s.equals("refine") || s.equals("补充") || s.equals("补充要求")) {
            return Choice.REFINE;
        }
        if (s.equals("c") || s.equals("cancel") || s.equals("取消")) {
            return Choice.CANCEL;
        }
        return null; // 非法输入
    }
}