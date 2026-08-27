package com.vccorp.eap.service.mapper;

import com.vccorp.eap.dto.DepartmentResponse;
import com.vccorp.eap.model.Department;
import org.springframework.stereotype.Component;

/**
 * Mapper chịu trách nhiệm chuyển đổi Department entity sang DepartmentResponse DTO.
 * Tách biệt hoàn toàn khỏi business logic trong DepartmentServiceImpl.
 */
@Component
public class DepartmentMapper {

    public DepartmentResponse mapToResponse(Department dept) {
        if (dept == null) return null;
        return DepartmentResponse.builder(dept.getId(), dept.getCode(), dept.getName())
                .description(dept.getDescription())
                .createdAt(dept.getCreatedAt())
                .updatedAt(dept.getUpdatedAt())
                .build();
    }
}
