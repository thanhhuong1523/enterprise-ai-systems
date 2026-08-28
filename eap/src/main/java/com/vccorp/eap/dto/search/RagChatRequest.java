package com.vccorp.eap.dto.search;

import jakarta.validation.constraints.NotBlank;

public record RagChatRequest(
    @NotBlank(message = "Tin nhắn không được để trống.") String message
) {
}
