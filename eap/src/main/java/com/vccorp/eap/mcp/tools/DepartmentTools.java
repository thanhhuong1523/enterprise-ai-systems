package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.department.CreateDepartmentRequest;
import com.vccorp.eap.dto.department.DepartmentResponse;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import com.vccorp.eap.service.department.DepartmentService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AI Tool Facade cho các nghiệp vụ liên quan đến Phòng ban.
 * Tuân thủ ADR-006.6: Tách biệt hoàn toàn ranh giới AI Facade và Domain Service.
 */
@Component
public class DepartmentTools implements McpToolFacade {

    private final DepartmentService departmentService;

    public DepartmentTools(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * Tool: Lấy toàn bộ danh sách các phòng ban đang hoạt động trong công ty.
     */
    @McpTool(
            name = "listDepartments",
            description = "Lấy toàn bộ danh sách các phòng ban đang hoạt động trong công ty."
    )
    public List<DepartmentResponse> listDepartments() {
        return departmentService.listDepartments();
    }

    /**
     * Tool: Đăng ký (tạo mới) một phòng ban trong hệ thống EAP.
     */
    @McpTool(
            name = "createDepartment",
            description = "Đăng ký (tạo mới) một phòng ban trong hệ thống EAP."
    )
    public DepartmentResponse createDepartment(
            @McpToolParam(description = "Mã viết tắt của phòng ban, viết HOA, không dấu tiếng Việt, không khoảng trắng, tương tự các mã trong hệ thống như HR, RND, KETOAN, FIN, BOARD (regex: ^[A-Z0-9_-]+$)") String code,
            @McpToolParam(description = "Tên phòng ban bằng tiếng Việt có dấu, không kèm chữ 'Phòng' ở đầu, tương tự như các tên trong cơ sở dữ liệu: 'Nhân sự', 'Phát triển', 'Kế toán', 'Tài chính', 'Ban Giám Đốc'") String name,
            @McpToolParam(description = "Mô tả chức năng nhiệm vụ của phòng ban", required = false) String description
    ) {
        CreateDepartmentRequest request = new CreateDepartmentRequest(code, name, description);
        DepartmentResponse response = departmentService.createDepartment(request);
        if (response == null) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể khởi tạo phòng ban.");
        }
        return response;
    }

    /**
     * Tool: Tìm kiếm thông tin chi tiết và mã định danh UUID của một phòng ban theo tên.
     */
    @McpTool(
            name = "getDepartmentByName",
            description = "Tìm kiếm thông tin chi tiết và mã định danh UUID của một phòng ban theo tên gọi tiếng Việt. Trả về Optional rỗng nếu không tìm thấy."
    )
    public Optional<DepartmentResponse> getDepartmentByName(
            @McpToolParam(description = "Tên phòng ban cần tra cứu (ví dụ: 'Nhân sự', 'Phát triển')") String name
    ) {
        Optional<DepartmentResponse> dept = departmentService.getDepartmentByName(name);
        if (dept.isEmpty()) {
            String deptName = (name != null && !name.trim().isEmpty()) ? name.trim() : "";
            String message = deptName.isEmpty()
                    ? "Không tìm thấy phòng ban trong hệ thống. Vui lòng kiểm tra lại tên phòng ban."
                    : "Không tìm thấy phòng ban '" + deptName + "' trong hệ thống. Vui lòng kiểm tra lại tên phòng ban.";
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND, message);
        }
        return dept;
    }

    /**
     * Tool: Cập nhật thông tin phòng ban (mã, tên, mô tả). Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN).
     */
    @McpTool(
            name = "updateDepartment",
            description = "Cập nhật thông tin phòng ban theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN)."
    )
    public DepartmentResponse updateDepartment(
            @McpToolParam(description = "Mã định danh UUID của phòng ban cần cập nhật (lấy từ getDepartmentByName)") java.util.UUID id,
            @McpToolParam(description = "Mã viết tắt mới của phòng ban (regex: ^[A-Z0-9_-]+$)", required = false) String code,
            @McpToolParam(description = "Tên mới của phòng ban bằng tiếng Việt có dấu", required = false) String name,
            @McpToolParam(description = "Mô tả chức năng nhiệm vụ mới của phòng ban", required = false) String description
    ) {
        com.vccorp.eap.dto.department.UpdateDepartmentRequest request = new com.vccorp.eap.dto.department.UpdateDepartmentRequest(code, name, description);
        return departmentService.updateDepartment(id, request);
    }

    /**
     * Tool: Xóa phòng ban khỏi hệ thống theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN).
     */
    @McpTool(
            name = "deleteDepartment",
            description = "Xóa phòng ban khỏi hệ thống theo mã định danh UUID. Chỉ dành cho Quản trị viên (ROLE_SYSTEM_ADMIN)."
    )
    public String deleteDepartment(
            @McpToolParam(description = "Mã định danh UUID của phòng ban cần xóa (lấy từ getDepartmentByName)") java.util.UUID id
    ) {
        departmentService.deleteDepartment(id);
        return "Đã xóa phòng ban thành công.";
    }
}

