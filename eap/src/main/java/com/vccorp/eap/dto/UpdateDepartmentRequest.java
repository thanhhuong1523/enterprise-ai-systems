package com.vccorp.eap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDepartmentRequest {
    @NotBlank(message = "Mã phòng ban không được để trống")
    private String code;

    @NotBlank(message = "Tên phòng ban không được để trống")
    private String name;

    private String description;
}
