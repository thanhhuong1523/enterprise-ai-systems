package com.vccorp.eap.dto;

import com.vccorp.eap.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private String confirmPassword;
    private Role role;
    private UUID departmentId;
    private String fullName;
    private String phone;
}
