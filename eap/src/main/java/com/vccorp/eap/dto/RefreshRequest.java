package com.vccorp.eap.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "Refresh token không được để trống") String refreshToken
) {}
