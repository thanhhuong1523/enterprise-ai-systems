package com.vccorp.eap.dto;

import com.vccorp.eap.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUserRequest(
    @NotBlank(message = "Tên đăng nhập không được để trống") String username,
    @NotBlank(message = "Email không được để trống") @Email(message = "Email không đúng định dạng") String email,
    @NotBlank(message = "Mật khẩu không được để trống") String password,
    @NotBlank(message = "Xác nhận mật khẩu không được để trống") String confirmPassword,
    @NotNull(message = "Vai trò không được để trống") Role role,
    UUID departmentId,
    @NotBlank(message = "Họ và tên không được để trống") String fullName,
    @NotBlank(message = "Số điện thoại không được để trống") String phone
) {
    // Java Bean style getters for compatibility
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getConfirmPassword() { return confirmPassword; }
    public Role getRole() { return role; }
    public UUID getDepartmentId() { return departmentId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }

    public static Builder builder(
        String username,
        String email,
        String password,
        String confirmPassword,
        Role role,
        String fullName,
        String phone
    ) {
        return new Builder(username, email, password, confirmPassword, role, fullName, phone);
    }

    public static class Builder {
        private final String username;
        private final String email;
        private final String password;
        private final String confirmPassword;
        private final Role role;
        private UUID departmentId;
        private final String fullName;
        private final String phone;

        public Builder(
            String username,
            String email,
            String password,
            String confirmPassword,
            Role role,
            String fullName,
            String phone
        ) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.confirmPassword = confirmPassword;
            this.role = role;
            this.fullName = fullName;
            this.phone = phone;
        }

        public Builder departmentId(UUID departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public CreateUserRequest build() {
            return new CreateUserRequest(username, email, password, confirmPassword, role, departmentId, fullName, phone);
        }
    }
}
