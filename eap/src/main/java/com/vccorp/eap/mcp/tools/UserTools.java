package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.user.CreateUserRequest;
import com.vccorp.eap.dto.user.UpdateUserRequest;
import com.vccorp.eap.dto.user.UserResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.user.UserService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * AI Tool Facade cho các nghiệp vụ liên quan đến Quản lý Người dùng / Nhân sự.
 * Tuân thủ ADR-006.6: Tách biệt hoàn toàn ranh giới AI Facade và Domain Service.
 */
@Component
public class UserTools implements McpToolFacade {

    private final UserService userService;

    public UserTools(UserService userService) {
        this.userService = userService;
    }

    /**
     * Tool: Tìm kiếm thông tin chi tiết và mã định danh UUID của nhân viên theo tên hoặc username.
     */
    @McpTool(
            name = "getUserByName",
            description = "Tra cứu thông tin chi tiết và mã định danh UUID của một nhân viên theo họ tên hoặc tên đăng nhập. Trả về thông tin nhân sự kèm UUID để phục vụ các bước xâu chuỗi tiếp theo."
    )
    public Object getUserByName(
            @McpToolParam(description = "Họ tên đầy đủ hoặc tên đăng nhập của nhân viên cần tra cứu") String name
    ) {
        if (name == null || name.trim().isEmpty()) {
            return "Tên người dùng không được để trống.";
        }
        String cleanName = name.trim();
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        try {
            return userService.getUserByName(cleanName);
        } catch (BusinessException ex) {
            if (currentUser != null && currentUser.getRole() != Role.SYSTEM_ADMIN) {
                if (ex.getErrorCode() == ErrorCode.ERR_FORBIDDEN_ROLE || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                    return "Không tìm thấy người dùng '" + cleanName + "' trong phòng ban của bạn. Bạn không có quyền tra cứu nhân sự thuộc phòng ban khác.";
                }
            } else {
                if (ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                    return "Không tìm thấy người dùng '" + cleanName + "' trong hệ thống.";
                }
            }
            throw ex;
        }
    }

    /**
     * Tool: Đăng ký (tạo mới) tài khoản nhân sự trong công ty. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN).
     */
    @McpTool(
            name = "createUser",
            description = "Đăng ký (tạo mới) tài khoản nhân sự trong công ty. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN)."
    )
    public UserResponse createUser(
            @McpToolParam(description = "Tên đăng nhập viết thường không dấu (ví dụ: hoangnv)") String username,
            @McpToolParam(description = "Email công ty định dạng @vccorp.vn") String email,
            @McpToolParam(description = "Mật khẩu cho tài khoản") String password,
            @McpToolParam(description = "Họ và tên đầy đủ của nhân viên") String fullName,
            @McpToolParam(description = "Số điện thoại liên hệ của nhân viên") String phone,
            @McpToolParam(description = "Mã UUID của phòng ban nhân viên trực thuộc (lấy từ getDepartmentByName)") UUID departmentId,
            @McpToolParam(description = "Vai trò người dùng (ROLE_EMPLOYEE, ROLE_DEPT_MANAGER, ROLE_BOARD). Mặc định là ROLE_EMPLOYEE", required = false) Role role,
            @McpToolParam(description = "Xác nhận mật khẩu (tự động đồng bộ bằng password nếu bỏ trống)", required = false) String confirmPassword
    ) {
        String finalConfirmPassword = (confirmPassword != null && !confirmPassword.trim().isEmpty())
                ? confirmPassword.trim()
                : password;
        Role finalRole = role != null ? role : Role.ROLE_EMPLOYEE;
        CreateUserRequest request = new CreateUserRequest(
                username, email, password, finalConfirmPassword, finalRole, departmentId, fullName, phone
        );
        return userService.createUser(request);
    }

    /**
     * Tool: Xem danh sách toàn bộ người dùng trong hệ thống. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN).
     */
    @McpTool(
            name = "listUsers",
            description = "Xem danh sách toàn bộ người dùng trong hệ thống. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN)."
    )
    public Object listUsers() {
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        if (currentUser == null || currentUser.getRole() != Role.SYSTEM_ADMIN) {
            return "Bạn không có quyền xem danh sách toàn bộ nhân sự trong công ty. Thao tác này chỉ dành cho Quản trị viên.";
        }
        List<UserResponse> list = userService.listUsers();
        return list != null ? list : java.util.Collections.emptyList();
    }

    /**
     * Tool: Xem danh sách nhân sự của một phòng ban theo UUID phòng ban.
     */
    @McpTool(
            name = "listUsersByDepartment",
            description = "Xem danh sách nhân sự thuộc một phòng ban cụ thể. Quản trị viên có thể xem bất kỳ phòng ban nào, người dùng khác chỉ xem được nhân sự phòng ban của chính mình."
    )
    public Object listUsersByDepartment(
            @McpToolParam(description = "Mã UUID của phòng ban cần xem danh sách nhân sự (lấy từ getDepartmentByName)") UUID departmentId
    ) {
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        if (currentUser == null) {
            return "Yêu cầu đăng nhập để xem danh sách nhân sự.";
        }
        if (currentUser.getRole() != Role.SYSTEM_ADMIN) {
            if (currentUser.getDepartmentId() == null || !currentUser.getDepartmentId().equals(departmentId)) {
                return "Bạn chỉ được phép xem danh sách nhân sự trong phòng ban của mình, không có quyền xem nhân sự của phòng ban khác.";
            }
        }
        List<UserResponse> list = userService.listUsersByDepartment(departmentId, currentUser);
        return list != null ? list : java.util.Collections.emptyList();
    }

    /**
     * Tool: Cập nhật thông tin nhân viên theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN).
     */
    @McpTool(
            name = "updateUser",
            description = "Cập nhật thông tin nhân viên (tên đăng nhập, email, họ tên, số điện thoại) theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN)."
    )
    public UserResponse updateUser(
            @McpToolParam(description = "Mã UUID của nhân viên cần cập nhật (lấy từ getUserByName)") UUID id,
            @McpToolParam(description = "Tên đăng nhập mới", required = false) String username,
            @McpToolParam(description = "Email mới", required = false) String email,
            @McpToolParam(description = "Họ và tên mới", required = false) String fullName,
            @McpToolParam(description = "Số điện thoại mới", required = false) String phone
    ) {
        UpdateUserRequest request = new UpdateUserRequest(username, email, fullName, phone);
        return userService.updateUser(id, request);
    }

    /**
     * Tool: Xóa tài khoản nhân viên theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN).
     */
    @McpTool(
            name = "deleteUser",
            description = "Xóa tài khoản nhân viên khỏi hệ thống theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN)."
    )
    public String deleteUser(
            @McpToolParam(description = "Mã UUID của nhân viên cần xóa (lấy từ getUserByName)") UUID id
    ) {
        userService.deleteUser(id);
        return "Đã xóa tài khoản nhân viên thành công.";
    }
}
