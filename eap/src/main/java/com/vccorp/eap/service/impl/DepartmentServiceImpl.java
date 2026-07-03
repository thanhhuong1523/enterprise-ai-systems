package com.vccorp.eap.service.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.util.ValidationUtils;
import com.vccorp.eap.dto.CreateDepartmentRequest;
import com.vccorp.eap.dto.DepartmentResponse;
import com.vccorp.eap.dto.UpdateDepartmentRequest;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.repository.UserRepository;
import com.vccorp.eap.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    private Department findDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban không tồn tại."));
    }

    private DepartmentResponse mapToResponse(Department dept) {
        if (dept == null) return null;
        return DepartmentResponse.builder()
                .id(dept.getId())
                .code(dept.getCode())
                .name(dept.getName())
                .description(dept.getDescription())
                .createdAt(dept.getCreatedAt())
                .updatedAt(dept.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(CreateDepartmentRequest request) {
        if (request.getCode() == null || request.getCode().trim().isEmpty() ||
            request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã hoặc tên phòng ban không được để trống.");
        }
        
        String cleanCode = request.getCode().trim().toUpperCase();
        if (cleanCode.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban không được vượt quá 50 ký tự.");
        }
        if (!ValidationUtils.isValidDepartmentCode(cleanCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban chỉ được chứa chữ cái, số, gạch dưới và gạch ngang.");
        }
        if (departmentRepository.existsByCode(cleanCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban đã tồn tại.");
        }

        String cleanName = request.getName().trim();
        if (cleanName.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban không được vượt quá 100 ký tự.");
        }
        if (departmentRepository.existsByName(cleanName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban đã tồn tại.");
        }

        String description = "Phòng ban nghiệp vụ chuyên trách trong hệ thống EAP.";
        if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
            description = request.getDescription().trim();
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

        return mapToResponse(departmentRepository.save(department));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentDetail(UUID id) {
        return mapToResponse(findDepartmentById(id));
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(UUID id, UpdateDepartmentRequest request) {
        Department dept = findDepartmentById(id);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String cleanName = request.getName().trim();
            if (cleanName.length() > 100) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban không được vượt quá 100 ký tự.");
            }
            if (!cleanName.equals(dept.getName()) && departmentRepository.existsByName(cleanName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên phòng ban đã tồn tại.");
            }
            dept.setName(cleanName);
        }
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            String cleanCode = request.getCode().trim().toUpperCase();
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
        if (request.getDescription() != null) {
            String desc = request.getDescription().trim();
            if (desc.length() > 500) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mô tả phòng ban không được vượt quá 500 ký tự.");
            }
            dept.setDescription(desc);
        }

        return mapToResponse(departmentRepository.save(dept));
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID id) {
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
}
