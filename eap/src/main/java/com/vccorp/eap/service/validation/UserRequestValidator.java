package com.vccorp.eap.service.validation;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.util.ValidationUtils;
import com.vccorp.eap.dto.user.CreateUserRequest;
import com.vccorp.eap.dto.user.UpdateUserRequest;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class UserRequestValidator {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public UserRequestValidator(UserRepository userRepository, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    public void validateCreateRequest(CreateUserRequest request) {
        if (request.username() == null || request.username().trim().isEmpty() ||
            request.email() == null || request.email().trim().isEmpty() ||
            request.password() == null || request.password().trim().isEmpty() ||
            request.confirmPassword() == null || request.confirmPassword().trim().isEmpty() ||
            request.role() == null || request.fullName() == null || request.fullName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tất cả các trường thông tin bắt buộc phải điền đầy đủ.");
        }

        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mật khẩu xác nhận không trùng khớp.");
        }

        if (request.role() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể tạo tài khoản quản trị hệ thống (SYSTEM_ADMIN).");
        }

        String usernameClean = request.username().trim();
        if (usernameClean.length() < 3 || usernameClean.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập phải có độ dài từ 3 đến 50 ký tự.");
        }
        if (!ValidationUtils.isValidUsername(usernameClean)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập chỉ được chứa chữ cái, số, dấu chấm, dấu gạch dưới và dấu gạch ngang.");
        }

        String emailClean = request.email().trim();
        if (emailClean.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Email không được vượt quá 100 ký tự.");
        }
        if (!ValidationUtils.isValidEmail(emailClean)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng email không hợp lệ.");
        }

        String fullNameClean = request.fullName().trim();
        if (fullNameClean.length() > 150) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Họ và tên không được vượt quá 150 ký tự.");
        }

        if (request.phone() != null && !request.phone().trim().isEmpty()) {
            String phoneClean = request.phone().trim();
            if (phoneClean.length() > 20) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số điện thoại không được vượt quá 20 ký tự.");
            }
        }

        if (userRepository.existsByUsernameOrEmail(usernameClean, emailClean)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập hoặc email đã tồn tại.");
        }

        if (request.departmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Người dùng nghiệp vụ bắt buộc phải gán phòng ban.");
        }

        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));

        if (department.getCode().equalsIgnoreCase("BOARD")) {
            if (request.role() != Role.ROLE_BOARD) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban Ban Giám Đốc (BOARD) chỉ cho phép gán vai trò BOARD.");
            }
        } else {
            if (request.role() != Role.ROLE_EMPLOYEE && request.role() != Role.ROLE_DEPT_MANAGER) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban này chỉ cho phép gán vai trò EMPLOYEE hoặc DEPT_MANAGER.");
            }
        }
    }

    public void validateUpdateRequest(User user, UpdateUserRequest request) {
        if (request.username() != null && !request.username().trim().isEmpty()) {
            String newUsername = request.username().trim();
            if (newUsername.length() < 3 || newUsername.length() > 50) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập phải có độ dài từ 3 đến 50 ký tự.");
            }
            if (!ValidationUtils.isValidUsername(newUsername)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập chỉ được chứa chữ cái, số, dấu chấm, dấu gạch dưới và dấu gạch ngang.");
            }
            if (!newUsername.equalsIgnoreCase(user.getUsername()) && userRepository.findByUsername(newUsername).isPresent()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập đã tồn tại.");
            }
        }

        if (request.email() != null && !request.email().trim().isEmpty()) {
            String newEmail = request.email().trim();
            if (newEmail.length() > 100) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Email không được vượt quá 100 ký tự.");
            }
            if (!ValidationUtils.isValidEmail(newEmail)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Định dạng email không hợp lệ.");
            }
            if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByUsernameOrEmail("", newEmail)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Email đã tồn tại.");
            }
        }

        if (request.fullName() != null && !request.fullName().trim().isEmpty()) {
            String cleanFullName = request.fullName().trim();
            if (cleanFullName.length() > 150) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Họ và tên không được vượt quá 150 ký tự.");
            }
        }

        if (request.phone() != null) {
            String cleanPhone = request.phone().trim();
            if (cleanPhone.length() > 20) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số điện thoại không được vượt quá 20 ký tự.");
            }
        }
    }
}
