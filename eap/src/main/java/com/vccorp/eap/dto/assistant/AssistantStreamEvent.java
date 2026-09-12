package com.vccorp.eap.dto.assistant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vccorp.eap.dto.document.ChunkResultDto;

import java.util.List;

/**
 * Phong bì (envelope) cấu trúc sự kiện SSE stream gửi về client.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantStreamEvent(
        Integer step,
        String status,
        String tool,
        String label,
        String text,
        String thought,
        String code,
        String message,
        List<ChunkResultDto> chunks
) {
    public static AssistantStreamEvent thinking(int step, String message) {
        return new AssistantStreamEvent(step, "REASONING", null, null, null, null, null, message, null);
    }

    public static AssistantStreamEvent reasoning(int step, String thought) {
        return new AssistantStreamEvent(step, "REASONING", null, null, null, thought, null, null, null);
    }

    public static AssistantStreamEvent actionStart(int step, String tool, String label) {
        return new AssistantStreamEvent(step, "CALLING_TOOL", tool, label, null, null, null, null, null);
    }

    public static AssistantStreamEvent actionEnd(int step, String tool, String status, String label) {
        return new AssistantStreamEvent(step, status, tool, label, null, null, null, null, null);
    }

    public static AssistantStreamEvent content(int step, String text) {
        return new AssistantStreamEvent(step, "COMPLETED", null, null, text, null, null, null, null);
    }

    public static AssistantStreamEvent content(int step, String text, List<ChunkResultDto> chunks) {
        return new AssistantStreamEvent(step, "COMPLETED", null, null, text, null, null, null, chunks);
    }

    public static AssistantStreamEvent error(String code, String message) {
        return new AssistantStreamEvent(null, "ERROR", null, null, null, null, code, message, null);
    }

    public static AssistantStreamEvent done() {
        return new AssistantStreamEvent(null, "FINISHED", null, null, null, null, null, null, null);
    }
}
