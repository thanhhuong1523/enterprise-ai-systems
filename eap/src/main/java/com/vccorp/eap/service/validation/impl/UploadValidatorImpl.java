package com.vccorp.eap.service.validation.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.validation.UploadValidator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lớp thực thi của UploadValidator.
 * Thực hiện kiểm tra chi tiết các logic phân quyền phòng ban và bảo vệ phòng Ban Giám đốc (BOARD).
 */
@Component
public class UploadValidatorImpl implements UploadValidator {

    private final DepartmentRepository departmentRepository;

    /**
     * Khởi tạo UploadValidatorImpl.
     */
    public UploadValidatorImpl(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUserRole(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED);
        }
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE, "Quản trị viên hệ thống không được phép thao tác tài liệu.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề và tệp đính kèm không được trống.");
        }
        if (title.trim().length() > 255) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tiêu đề tài liệu không được vượt quá 255 ký tự.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateUserDepartment(User user) {
        if (user.getDepartmentId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Người dùng chưa được gán vào phòng ban. Vui lòng liên hệ quản trị viên.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAliasTargetDepartment(UUID aliasDepartmentId) {
        if (isBoardDepartment(aliasDepartmentId)) {
            throw new BusinessException(ErrorCode.ERR_BOARD_PROTECTION,
                    "Không thể chia sẻ tài liệu đến phòng Ban Giám Đốc.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBoardDepartment(UUID deptId) {
        if (deptId == null) return false;
        return departmentRepository.findById(deptId)
                .map(d -> "BOARD".equalsIgnoreCase(d.getCode()))
                .orElse(false);
    }
}
