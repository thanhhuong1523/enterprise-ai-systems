package com.vccorp.eap.controller.assistant;

import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.mcp.orchestrator.AgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantStreamControllerTest {

    @Mock
    private AgentOrchestrator agentOrchestrator;

    private AssistantStreamController controller;

    @BeforeEach
    void setUp() {
        controller = new AssistantStreamController(agentOrchestrator);
    }

    @Test
    void testStreamAssistantChat_DelegatesToAgentOrchestrator() {
        RagChatRequest request = new RagChatRequest("Cho tôi xem danh sách phòng ban");
        SseEmitter mockEmitter = new SseEmitter();
        when(agentOrchestrator.streamChat("Cho tôi xem danh sách phòng ban")).thenReturn(mockEmitter);

        SseEmitter result = controller.streamAssistantChat(request);

        assertSame(mockEmitter, result);
        verify(agentOrchestrator, times(1)).streamChat("Cho tôi xem danh sách phòng ban");
    }
}

