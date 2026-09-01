package com.agentcli.llm;

/**
 * 一条对话消息。
 *
 * @param role    角色：system / user / assistant
 * @param content 消息文本
 */
public record Message(String role, String content) {
}