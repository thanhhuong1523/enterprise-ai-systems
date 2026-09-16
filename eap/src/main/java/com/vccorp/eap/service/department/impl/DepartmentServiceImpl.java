package com.vccorp.eap.service.department.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.util.ValidationUtils;
import com.vccorp.eap.dto.department.CreateDepartmentRequest;
import com.vccorp.eap.dto.department.DepartmentResponse;
import com.vccorp.eap.dto.department.UpdateDepartmentRequest;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.department.DepartmentService;
import com.vccorp.eap.service.mapper.DepartmentMapper;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DepartmentMapper departmentMapper;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository,
                                  UserRepository userRepository,
                                  DocumentRepository documentRepository,
                                  DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.departmentMapper = departmentMapper;
    }

    private Department findDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        if (request.code() == null || request.code().trim().isEmpty() ||
            request.name() == null || request.name().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã hoặc tên phòng ban không được để trống.");
        }
        
        String cleanCode = request.code().trim().toUpperCase();
        if (cleanCode.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban không được vượt quá 50 ký tự.");
        }
        if (!ValidationUtils.isValidDepartmentCode(cleanCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban chỉ được chứa chữ cái, số, gạch dưới và gạch ngang.");
        }
        if (departmentRepository.existsByCode(cleanCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban đã tồn tại.");
        }

        String cleanName = request.name().trim();
        if (cleanName.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban không được vượt quá 100 ký tự.");
        }
        if (departmentRepository.existsByName(cleanName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban đã tồn tại.");
        }

        String description = "Phòng ban nghiệp vụ chuyên trách trong hệ thống EAP.";
        if (request.description() != null && !request.description().trim().isEmpty()) {
            description = request.description().trim();
            if (description.length() > 500) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mô tả phòng ban không được vượt quá 500 ký tự.");
            }
        }

        Department department = Department.builder()
                .id(UUID.randomUUID())
                .code(cleanCode)
                .name(cleanName)
                .description(description)
                .build();

        return departmentMapper.mapToResponse(departmentRepository.save(department));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        SecurityContextHelper.getCurrentUser();
        return departmentRepository.findAll().stream()
                .map(departmentMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentDetail(UUID id) {
        SecurityContextHelper.getCurrentUser();
        return departmentMapper.mapToResponse(findDepartmentById(id));
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        Department dept = findDepartmentById(id);

        if (request.name() != null && !request.name().trim().isEmpty()) {
            String cleanName = request.name().trim();
            if (cleanName.length() > 100) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban không được vượt quá 100 ký tự.");
            }
            if (!cleanName.equals(dept.getName()) && departmentRepository.existsByName(cleanName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban đã tồn tại.");
            }
            dept.setName(cleanName);
        }
        if (request.code() != null && !request.code().trim().isEmpty()) {
            String cleanCode = request.code().trim().toUpperCase();
            if (cleanCode.length() > 50) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban không được vượt quá 50 ký tự.");
            }
            if (!ValidationUtils.isValidDepartmentCode(cleanCode)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban chỉ được chứa chữ cái, số, gạch dưới và gạch ngang.");
            }
            if (!cleanCode.equals(dept.getCode()) && departmentRepository.existsByCode(cleanCode)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban đã tồn tại.");
            }
            dept.setCode(cleanCode);
        }
        if (request.description() != null) {
            String desc = request.description().trim();
            if (desc.length() > 500) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mô tả phòng ban không được vượt quá 500 ký tự.");
            }
            dept.setDescription(desc);
        }

        return departmentMapper.mapToResponse(departmentRepository.save(dept));
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID id) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE);
        }

        Department dept = findDepartmentById(id);
        
        if (userRepository.existsByDepartmentId(id)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể xóa phòng ban đang có nhân viên.");
        }
        if (documentRepository.existsByOwnerDepartmentIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể xóa phòng ban đang chứa tài liệu.");
        }

        dept.setDeletedAt(LocalDateTime.now());
        departmentRepository.save(dept);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<DepartmentResponse> getDepartmentByName(String name) {
        SecurityContextHelper.getCurrentUser();
        if (name == null || name.trim().isEmpty()) {
            return java.util.Optional.empty();
        }
        String cleanName = name.trim();
        return departmentRepository.findByNameIgnoreCase(cleanName)
                .or(() -> departmentRepository.findByCodeIgnoreCase(cleanName))
                .map(departmentMapper::mapToResponse);
    }
}
