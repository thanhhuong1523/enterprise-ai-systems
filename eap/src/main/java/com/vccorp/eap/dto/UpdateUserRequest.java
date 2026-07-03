package com.vccorp.eap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for updating a user's profile information.
 * NOTE: 'role' is intentionally excluded — role is immutable after account creation.
 */
@Data
public class UpdateUserRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;
}
