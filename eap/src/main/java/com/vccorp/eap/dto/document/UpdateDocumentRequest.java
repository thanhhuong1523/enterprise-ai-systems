package com.vccorp.eap.dto.document;

import jakarta.validation.constraints.NotBlank;

public record UpdateDocumentRequest(
    @NotBlank(message = "Tiêu đề không được để trống") String title
) {}
