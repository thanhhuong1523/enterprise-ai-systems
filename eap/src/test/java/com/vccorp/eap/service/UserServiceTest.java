package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.user.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import com.vccorp.eap.service.cache.RedisService;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private com.vccorp.eap.service.mapper.UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User adminCaller;
    private User employeeCaller;

    @BeforeEach
    void setUp() {
        adminCaller = User.builder()
                .id(UUID.randomUUID())
                .username("admin")
                .role(Role.SYSTEM_ADMIN)
                .build();

        employeeCaller = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .departmentId(UUID.randomUUID())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(adminCaller.getRole().name())))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteUser_SystemAdmin_ThrowsValidationException() {
        UUID adminId = UUID.randomUUID();
        User adminUser = User.builder()
                .id(adminId)
                .username("admin")
                .role(Role.SYSTEM_ADMIN)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.deleteUser(adminId));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals("Không thể xóa tài khoản quản trị hệ thống (SYSTEM_ADMIN).", ex.getMessage());
    }

    @Test
    void deleteUser_NonAdmin_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        UUID targetUserId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.deleteUser(targetUserId));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
    }

    @Test
    void listUsers_NonAdmin_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.listUsers());
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
    }

    @Test
    void getUserDetail_NonAdmin_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        UUID targetUserId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserDetail(targetUserId));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
    }

    @Test
    void deleteUser_Unauthenticated_ThrowsUnauthenticated() {
        SecurityContextHolder.clearContext();

        UUID targetUserId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.deleteUser(targetUserId));
        assertEquals(ErrorCode.ERR_UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void getUserByName_NonAdmin_DifferentDepartment_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        User otherDeptUser = User.builder()
                .id(UUID.randomUUID())
                .username("other")
                .fullName("Khác Phòng Ban")
                .departmentId(UUID.randomUUID()) // khác employeeCaller.departmentId
                .role(Role.ROLE_EMPLOYEE)
                .build();

        when(userRepository.findByFullNameIgnoreCaseAndDeletedAtIsNull("Khác Phòng Ban"))
                .thenReturn(Optional.of(otherDeptUser));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getUserByName("Khác Phòng Ban"));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
        assertEquals("Bạn chỉ được phép tra cứu thông tin người dùng trong phòng ban của mình.", ex.getMessage());
    }

    @Test
    void listUsersByDepartment_NonAdmin_DifferentDepartment_ThrowsForbiddenRole() {
        UUID otherDeptId = UUID.randomUUID();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.listUsersByDepartment(otherDeptId, employeeCaller));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
        assertEquals("Bạn chỉ được phép xem danh sách nhân sự trong phòng ban của mình.", ex.getMessage());
    }
}
