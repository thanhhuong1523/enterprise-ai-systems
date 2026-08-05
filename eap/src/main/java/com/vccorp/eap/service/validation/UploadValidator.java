package com.vccorp.eap.service.validation;

import com.vccorp.eap.model.User;
import java.util.UUID;

/**
 * Interface chịu trách nhiệm kiểm tra các ràng buộc nghiệp vụ khi thực hiện tải lên tài liệu.
 */
public interface UploadValidator {
    
    /**
     * Kiểm tra quyền của người dùng thao tác tài liệu (ví dụ: quản trị viên hệ thống không được phép upload).
     */
    void validateUserRole(User user);
    
    /**
     * Kiểm tra tính hợp lệ của tiêu đề tài liệu (không trống, độ dài không vượt quá 255 ký tự).
     */
    void validateTitle(String title);
    
    /**
     * Xác thực thông tin phòng ban của người dùng (người dùng bắt buộc phải thuộc một phòng ban cụ thể).
     */
    void validateUserDepartment(User user);
    
    /**
     * Kiểm tra phòng ban nhận liên kết (Alias). Ngăn chặn việc chia sẻ tài liệu đến phòng Ban Giám đốc (BOARD).
     */
    void validateAliasTargetDepartment(UUID aliasDepartmentId);
    
    /**
     * Kiểm tra phòng ban có phải là Ban Giám đốc (BOARD) hay không.
     */
    boolean isBoardDepartment(UUID deptId);
}
