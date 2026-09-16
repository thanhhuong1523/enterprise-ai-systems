package com.vccorp.eap.dto.department;

import jakarta.validation.constraints.NotBlank;

public record CreateDepartmentRequest(
    @NotBlank(message = "Mã phòng ban không được để trống") String code,
    @NotBlank(message = "Tên phòng ban không được để trống") String name,
    String description
) {}
