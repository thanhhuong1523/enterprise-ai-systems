package com.vccorp.eap.dto;

import lombok.Data;

@Data
public class UpdateDepartmentRequest {
    private String code;
    private String name;
    private String description;
}
