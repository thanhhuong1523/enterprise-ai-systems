package com.vccorp.eap.service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.CreateDepartmentRequest;
import com.vccorp.eap.dto.UpdateDepartmentRequest;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final com.vccorp.eap.repository.UserRepository userRepository;
    private final com.vccorp.eap.repository.DocumentRepository documentRepository;

    @Transactional
    public Department createDepartment(CreateDepartmentRequest request) {
        if (request.getCode() == null || request.getCode().trim().isEmpty() ||
            request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã hoặc tên phòng ban không được để trống.");
        }
        
        String cleanCode = request.getCode().trim().toUpperCase();
        if (cleanCode.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Mã phòng ban không được vượt quá 50 ký tự.");
        }
        if (!cleanCode.matches("^[A-Z0-9_-]+$")) {
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

        return departmentRepository.save(department);
    }

    @Transactional(readOnly = true)
    public List<Department> listDepartments() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Department getDepartmentDetail(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Phòng ban không tồn tại."));
    }

    @Transactional
    public Department updateDepartment(UUID id, UpdateDepartmentRequest request) {
        Department dept = getDepartmentDetail(id);

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
            if (!cleanCode.matches("^[A-Z0-9_-]+$")) {
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

        return departmentRepository.save(dept);
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        Department dept = getDepartmentDetail(id);
        
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
