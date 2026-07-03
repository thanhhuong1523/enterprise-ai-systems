package com.vccorp.eap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDocumentRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;
}
