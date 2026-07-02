package com.vccorp.eap.dto;

import lombok.Data;

/**
 * Request DTO for updating a user's profile information.
 * NOTE: 'role' is intentionally excluded — role is immutable after account creation.
 */
@Data
public class UpdateUserRequest {
    private String username;
    private String email;
    private String fullName;
    private String phone;
}
