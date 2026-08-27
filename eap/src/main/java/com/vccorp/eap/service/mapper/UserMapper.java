package com.vccorp.eap.service.mapper;

import com.vccorp.eap.dto.UserResponse;
import com.vccorp.eap.model.User;
import org.springframework.stereotype.Component;

/**
 * Mapper chịu trách nhiệm chuyển đổi User entity sang UserResponse DTO.
 * Tách biệt hoàn toàn khỏi business logic trong UserServiceImpl.
 */
@Component
public class UserMapper {

    public UserResponse mapToResponse(User user) {
        if (user == null) return null;
        return UserResponse.builder(user.getId(), user.getUsername(), user.getEmail(), user.getRole())
                .departmentId(user.getDepartmentId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
