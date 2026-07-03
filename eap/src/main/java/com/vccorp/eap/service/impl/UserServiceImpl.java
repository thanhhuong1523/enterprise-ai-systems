package com.vccorp.eap.service.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.util.ValidationUtils;
import com.vccorp.eap.dto.CreateUserRequest;
import com.vccorp.eap.dto.UpdateUserRequest;
import com.vccorp.eap.dto.UserResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.RedisService;
import com.vccorp.eap.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;

    public UserServiceImpl(UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           PasswordEncoder passwordEncoder,
                           RedisService redisService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisService = redisService;
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Người dùng không tồn tại."));
    }

    private UserResponse mapToResponse(User user) {
        if (user == null) return null;
        return UserResponse.builder(user.getId(), user.getUsername(), user.getEmail(), user.getRole())
                .departmentId(user.getDepartmentId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
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

        String phoneClean = null;
        if (request.phone() != null && !request.phone().trim().isEmpty()) {
            phoneClean = request.phone().trim();
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
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban chỉ định không tồn tại."));

        if (department.getCode().equalsIgnoreCase("BOARD")) {
            if (request.role() != Role.ROLE_BOARD) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban Ban Giám Đốc (BOARD) chỉ cho phép gán vai trò BOARD.");
            }
        } else {
            if (request.role() != Role.ROLE_EMPLOYEE && request.role() != Role.ROLE_DEPT_MANAGER) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban này chỉ cho phép gán vai trò EMPLOYEE hoặc DEPT_MANAGER.");
            }
        }

        User user = User.builder()
                .id(UUID.randomUUID())
                .username(usernameClean)
                .email(emailClean)
                .passwordHash(passwordEncoder.encode(request.password().trim()))
                .role(request.role())
                .departmentId(request.departmentId())
                .fullName(fullNameClean)
                .phone(phoneClean)
                .build();

        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserDetail(UUID id) {
        return mapToResponse(findUserById(id));
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findUserById(id);

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
            user.setUsername(newUsername);
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
            user.setEmail(newEmail);
        }

        if (request.fullName() != null && !request.fullName().trim().isEmpty()) {
            String cleanFullName = request.fullName().trim();
            if (cleanFullName.length() > 150) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Họ và tên không được vượt quá 150 ký tự.");
            }
            user.setFullName(cleanFullName);
        }

        if (request.phone() != null) {
            String cleanPhone = request.phone().trim();
            if (cleanPhone.length() > 20) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số điện thoại không được vượt quá 20 ký tự.");
            }
            user.setPhone(cleanPhone);
        }

        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = findUserById(id);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể xóa tài khoản quản trị hệ thống (SYSTEM_ADMIN).");
        }
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // Evict user exists cache key from Redis
        try {
            redisService.delete("user_exists:" + id);
        } catch (Exception e) {
            // Ignore Redis exception to prevent breaking business transaction
        }
    }
}
