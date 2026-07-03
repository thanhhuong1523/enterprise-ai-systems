package com.vccorp.eap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDepartmentRequest {
    @NotBlank(message = "Mã phòng ban không được để trống")
    private String code;

    @NotBlank(message = "Tên phòng ban không được để trống")
    private String name;

    private String description;
}
