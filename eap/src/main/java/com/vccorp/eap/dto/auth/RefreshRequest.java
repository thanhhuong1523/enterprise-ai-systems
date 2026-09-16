package com.vccorp.eap.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "Refresh token không được để trống") String refreshToken
) {}
