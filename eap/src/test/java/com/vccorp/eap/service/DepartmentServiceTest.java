package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.department.CreateDepartmentRequest;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.dto.department.UpdateDepartmentRequest;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.department.impl.DepartmentServiceImpl;
import com.vccorp.eap.service.mapper.DepartmentMapper;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

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
    void createDepartment_AsAdmin_Success() {
        CreateDepartmentRequest request = new CreateDepartmentRequest("FIN", "Finance", "Finance Dept");

        when(departmentRepository.existsByCode("FIN")).thenReturn(false);
        when(departmentRepository.existsByName("Finance")).thenReturn(false);

        Department dept = Department.builder()
                .id(UUID.randomUUID())
                .code("FIN")
                .name("Finance")
                .description("Finance Dept")
                .build();
        DepartmentResponse response = DepartmentResponse.builder(dept.getId(), "FIN", "Finance")
                .description("Finance Dept")
                .build();

        when(departmentRepository.save(any(Department.class))).thenReturn(dept);
        when(departmentMapper.mapToResponse(any(Department.class))).thenReturn(response);

        DepartmentResponse result = departmentService.createDepartment(request);
        assertNotNull(result);
        assertEquals("FIN", result.code());
    }

    @Test
    void createDepartment_AsEmployee_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        CreateDepartmentRequest request = new CreateDepartmentRequest("FIN", "Finance", "Finance Dept");
        BusinessException ex = assertThrows(BusinessException.class, () -> departmentService.createDepartment(request));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartment_AsEmployee_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        UUID deptId = UUID.randomUUID();
        UpdateDepartmentRequest request = new UpdateDepartmentRequest("New Name", "FIN_NEW", "Updated");
        BusinessException ex = assertThrows(BusinessException.class, () -> departmentService.updateDepartment(deptId, request));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
    }

    @Test
    void deleteDepartment_AsEmployee_ThrowsForbiddenRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        UUID deptId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class, () -> departmentService.deleteDepartment(deptId));
        assertEquals(ErrorCode.ERR_FORBIDDEN_ROLE, ex.getErrorCode());
    }

    @Test
    void deleteDepartment_Unauthenticated_ThrowsUnauthenticated() {
        SecurityContextHolder.clearContext();

        UUID deptId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class, () -> departmentService.deleteDepartment(deptId));
        assertEquals(ErrorCode.ERR_UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void listDepartments_AsEmployee_Success() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(employeeCaller, null,
                        Collections.singletonList(new SimpleGrantedAuthority(employeeCaller.getRole().name())))
        );

        when(departmentRepository.findAll()).thenReturn(Collections.emptyList());

        List<DepartmentResponse> result = departmentService.listDepartments();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listDepartments_Unauthenticated_ThrowsUnauthenticated() {
        SecurityContextHolder.clearContext();

        BusinessException ex = assertThrows(BusinessException.class, () -> departmentService.listDepartments());
        assertEquals(ErrorCode.ERR_UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void getDepartmentByName_FoundByName_Success() {
        Department dept = Department.builder()
                .id(UUID.randomUUID())
                .code("HR")
                .name("Nhân sự")
                .build();
        DepartmentResponse response = DepartmentResponse.builder(dept.getId(), "HR", "Nhân sự").build();

        when(departmentRepository.findByNameIgnoreCase("Nhân sự")).thenReturn(Optional.of(dept));
        when(departmentMapper.mapToResponse(dept)).thenReturn(response);

        Optional<DepartmentResponse> result = departmentService.getDepartmentByName("Nhân sự");
        assertTrue(result.isPresent());
        assertEquals("HR", result.get().code());
        assertEquals("Nhân sự", result.get().name());
    }

    @Test
    void getDepartmentByName_FallbackToCode_Success() {
        Department dept = Department.builder()
                .id(UUID.randomUUID())
                .code("RND")
                .name("Phát triển")
                .build();
        DepartmentResponse response = DepartmentResponse.builder(dept.getId(), "RND", "Phát triển").build();

        when(departmentRepository.findByNameIgnoreCase("RND")).thenReturn(Optional.empty());
        when(departmentRepository.findByCodeIgnoreCase("RND")).thenReturn(Optional.of(dept));
        when(departmentMapper.mapToResponse(dept)).thenReturn(response);

        Optional<DepartmentResponse> result = departmentService.getDepartmentByName("RND");
        assertTrue(result.isPresent());
        assertEquals("RND", result.get().code());
    }

    @Test
    void getDepartmentByName_NotFound_ReturnsEmpty() {
        when(departmentRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
        when(departmentRepository.findByCodeIgnoreCase("Unknown")).thenReturn(Optional.empty());

        Optional<DepartmentResponse> result = departmentService.getDepartmentByName("Unknown");
        assertTrue(result.isEmpty());
    }
}
