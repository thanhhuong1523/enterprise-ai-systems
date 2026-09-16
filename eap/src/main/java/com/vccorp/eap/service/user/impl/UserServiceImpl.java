package com.vccorp.eap.service.user.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.user.CreateUserRequest;
import com.vccorp.eap.dto.user.UpdateUserRequest;
import com.vccorp.eap.dto.user.UserResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.cache.RedisService;
import com.vccorp.eap.service.user.UserService;
import com.vccorp.eap.service.mapper.UserMapper;
import com.vccorp.eap.service.validation.UserRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;
    private final UserMapper userMapper;
    private final UserRequestValidator userRequestValidator;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           RedisService redisService,
                           UserMapper userMapper,
                           UserRequestValidator userRequestValidator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisService = redisService;
        this.userMapper = userMapper;
        this.userRequestValidator = userRequestValidator;
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        userRequestValidator.validateCreateRequest(request);

        String usernameClean = request.username().trim();
        String emailClean = request.email().trim();
        String fullNameClean = request.fullName().trim();
        String phoneClean = request.phone() != null ? request.phone().trim() : null;

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

        return userMapper.mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        return userRepository.findAll().stream()
                .map(userMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserDetail(UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        return userMapper.mapToResponse(findUserById(id));
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        User user = findUserById(id);
        userRequestValidator.validateUpdateRequest(user, request);

        if (request.username() != null && !request.username().trim().isEmpty()) {
            user.setUsername(request.username().trim());
        }
        if (request.email() != null && !request.email().trim().isEmpty()) {
            user.setEmail(request.email().trim());
        }
        if (request.fullName() != null && !request.fullName().trim().isEmpty()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }

        return userMapper.mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        User user = findUserById(id);
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể xóa tài khoản quản trị hệ thống (SYSTEM_ADMIN).");
        }
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        
        // Evict user exists cache key from Redis
        try {
            redisService.delete("user_exists:" + id);
        } catch (RuntimeException e) {
            log.warn("Failed to evict Redis cache for user {}: {}", id, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Tên người dùng không được để trống.");
        }
        User currentUser = SecurityContextHelper.getCurrentUser();
        String cleanName = name.trim();
        User user = userRepository.findByFullNameIgnoreCaseAndDeletedAtIsNull(cleanName)
                .or(() -> userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(cleanName))
                .or(() -> {
                    List<User> matches = userRepository.findByFullNameContainingIgnoreCaseOrUsernameContainingIgnoreCase(cleanName, cleanName);
                    return matches.stream().filter(u -> u.getDeletedAt() == null).findFirst();
                })
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "Không tìm thấy người dùng '" + cleanName + "' trong hệ thống. Vui lòng kiểm tra lại họ tên hoặc tên đăng nhập."));

        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            if (user.getDepartmentId() == null || !user.getDepartmentId().equals(currentUser.getDepartmentId())) {
                throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Bạn chỉ được phép tra cứu thông tin người dùng trong phòng ban của mình.");
            }
        }

        return userMapper.mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsersByDepartment(UUID departmentId, User currentUser) {
        if (departmentId == null) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Mã phòng ban không được để trống.");
        }
        User effectiveUser = currentUser != null ? currentUser : SecurityContextHelper.getCurrentUser();
        if (effectiveUser.getRole() != Role.SYSTEM_ADMIN) {
            if (effectiveUser.getDepartmentId() == null || !effectiveUser.getDepartmentId().equals(departmentId)) {
                throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Bạn chỉ được phép xem danh sách nhân sự trong phòng ban của mình.");
            }
        }
        return userRepository.findByDepartmentIdAndDeletedAtIsNull(departmentId).stream()
                .map(userMapper::mapToResponse)
                .collect(Collectors.toList());
    }
}
