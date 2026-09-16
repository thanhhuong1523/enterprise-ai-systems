package com.vccorp.eap.controller.assistant;

import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.mcp.orchestrator.AgentOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST / SSE Controller tiếp nhận yêu cầu trò chuyện của Trợ lý AI.
 * Controller tuân thủ nguyên tắc Clean Architecture: chỉ tiếp nhận request và trả về response,
 * toàn bộ logic khởi tạo kết nối SSE, timeout và phân phối luồng được uỷ quyền cho AgentOrchestrator.
 */
@RestController
@RequestMapping("/api/v1/ai/assistant")
@CrossOrigin
public class AssistantStreamController {

    private final AgentOrchestrator agentOrchestrator;

    public AssistantStreamController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssistantChat(@Valid @RequestBody RagChatRequest request) {
        return agentOrchestrator.streamChat(request.message());
    }
}

