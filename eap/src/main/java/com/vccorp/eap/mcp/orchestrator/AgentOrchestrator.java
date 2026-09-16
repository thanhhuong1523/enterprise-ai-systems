package com.vccorp.eap.mcp.orchestrator;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Giao diện điều phối luồng trợ lý AI tự chủ.
 */
public interface AgentOrchestrator {
    SseEmitter streamChat(String userPrompt);
    void executeStream(String userPrompt, SseEmitter emitter);
}
