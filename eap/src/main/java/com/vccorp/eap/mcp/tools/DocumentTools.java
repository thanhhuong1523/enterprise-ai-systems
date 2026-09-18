package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.document.CreateAliasRequest;
import com.vccorp.eap.dto.document.DocumentResponse;
import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.document.DocumentService;
import com.vccorp.eap.service.search.RetrievalService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * AI Tool Facade cho các nghiệp vụ liên quan đến tra cứu, quản trị và chia sẻ tài liệu.
 * Tuân thủ ADR-006.6: Tách biệt hoàn toàn ranh giới AI Facade và Domain Service.
 */
@Component
public class DocumentTools implements McpToolFacade {

    private final RetrievalService retrievalService;
    private final DocumentService documentService;

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentTools(RetrievalService retrievalService, DocumentService documentService) {
        this.retrievalService = retrievalService;
        this.documentService = documentService;
    }

    /**
     * Tương thích ngược cho các test chỉ mock RetrievalService
     */
    public DocumentTools(RetrievalService retrievalService) {
        this(retrievalService, null);
    }

    /**
     * Tool: Tìm kiếm thông tin từ kho tài liệu nội bộ dựa trên câu hỏi hoặc từ khóa tra cứu.
     */
    @McpTool(
            name = "searchDocuments",
            description = "Tìm kiếm thông tin từ kho tài liệu và dữ liệu nội bộ của hệ thống. "
                    + "Sử dụng công cụ này khi cần tra cứu hoặc tìm kiếm thông tin để trả lời câu hỏi của người dùng."
    )
    public RagChatResponse searchDocuments(
            @McpToolParam(description = "Câu hỏi hoặc từ khóa tìm kiếm thông tin") String query
    ) {
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        RagChatResponse response = retrievalService.search(new RagChatRequest(query), currentUser);
        return response != null ? response : new RagChatResponse("Không tìm thấy thông tin phù hợp.", java.util.Collections.emptyList());
    }

    /**
     * Tool: Tìm kiếm thông tin chi tiết và mã định danh UUID của tài liệu theo tiêu đề.
     */
    @McpTool(
            name = "getDocumentByTitle",
            description = "Tra cứu tài liệu theo tiêu đề trong kho tri thức của phòng ban để lấy mã định danh UUID phục vụ cho các thao tác cập nhật, xóa, hoặc chia sẻ liên kết Alias."
    )
    public Object getDocumentByTitle(
            @McpToolParam(description = "Tiêu đề của tài liệu cần tra cứu (ví dụ: 'Quy chế lương thưởng 2026')") String title
    ) {
        if (title == null || title.trim().isEmpty()) {
            return "Tiêu đề tài liệu không được để trống.";
        }
        String cleanTitle = title.trim();
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        if (currentUser == null) {
            return "Yêu cầu đăng nhập để tra cứu tài liệu.";
        }
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            return "Tài khoản Quản trị viên không có quyền truy cập kho tài liệu nghiệp vụ phòng ban.";
        }
        try {
            return documentService.getDocumentByTitle(cleanTitle, currentUser);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.ERR_DOCUMENT_NOT_FOUND) {
                return "Không tìm thấy tài liệu có tiêu đề '" + cleanTitle + "' trong kho tri thức của phòng ban bạn.";
            }
            if (ex.getErrorCode() == ErrorCode.ERR_FORBIDDEN_ROLE) {
                return "Bạn không có quyền truy cập tài liệu này.";
            }
            throw ex;
        }
    }

    /**
     * Tool: Xem danh sách các tài liệu gốc thuộc sở hữu của phòng ban người dùng hiện tại.
     */
    @McpTool(
            name = "listOriginalDocuments",
            description = "Lấy danh sách các tài liệu gốc thuộc sở hữu của phòng ban người dùng hiện tại."
    )
    public List<DocumentResponse> listOriginalDocuments(
            @McpToolParam(description = "Số trang (bắt đầu từ 0). Mặc định là 0", required = false) Integer page,
            @McpToolParam(description = "Số lượng bản ghi trên một trang. Mặc định là 10", required = false) Integer size
    ) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        int pageIndex = page != null ? page : 0;
        int pageSize = size != null ? size : 10;
        var pageResult = documentService.listOriginalDocuments(pageIndex, pageSize, currentUser);
        return (pageResult != null && pageResult.getContent() != null)
                ? pageResult.getContent()
                : java.util.Collections.emptyList();
    }

    /**
     * Tool: Cập nhật tiêu đề tài liệu gốc theo mã định danh UUID.
     */
    @McpTool(
            name = "updateOriginalDocument",
            description = "Cập nhật tiêu đề tài liệu gốc theo mã định danh UUID. Người thực hiện phải thuộc phòng ban sở hữu tài liệu và có vai trò Trưởng phòng (ROLE_DEPT_MANAGER) hoặc Ban Giám đốc (ROLE_BOARD)."
    )
    public Object updateOriginalDocument(
            @McpToolParam(description = "Mã UUID của tài liệu gốc cần cập nhật (lấy từ getDocumentByTitle)") UUID id,
            @McpToolParam(description = "Tiêu đề mới của tài liệu") String title
    ) {
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        if (currentUser == null) {
            return "Yêu cầu đăng nhập để cập nhật tài liệu.";
        }
        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER && currentUser.getRole() != Role.ROLE_BOARD) {
            return "Bạn không có quyền cập nhật tài liệu. Thao tác chỉ dành cho Trưởng phòng (ROLE_DEPT_MANAGER) hoặc Ban Giám đốc (ROLE_BOARD).";
        }
        return documentService.updateOriginalDocument(id, title, currentUser);
    }

    /**
     * Tool: Xóa tài liệu gốc khỏi kho tri thức theo mã định danh UUID.
     */
    @McpTool(
            name = "deleteOriginalDocument",
            description = "Xóa tài liệu gốc khỏi kho tri thức theo mã định danh UUID. Chỉ Trưởng phòng (ROLE_DEPT_MANAGER) hoặc Ban Giám đốc (ROLE_BOARD) mới có quyền xóa."
    )
    public Object deleteOriginalDocument(
            @McpToolParam(description = "Mã UUID của tài liệu gốc cần xóa (lấy từ getDocumentByTitle)") UUID id
    ) {
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        if (currentUser == null) {
            return "Yêu cầu đăng nhập để xóa tài liệu.";
        }
        if (currentUser.getRole() != Role.ROLE_DEPT_MANAGER && currentUser.getRole() != Role.ROLE_BOARD) {
            return "Bạn không có quyền xóa tài liệu. Thao tác chỉ dành cho Trưởng phòng (ROLE_DEPT_MANAGER) hoặc Ban Giám đốc (ROLE_BOARD).";
        }
        documentService.deleteOriginalDocument(id, currentUser);
        return "Đã xóa tài liệu gốc thành công.";
    }

    /**
     * Tool: Thiết lập liên kết chia sẻ tài liệu (tạo Alias) từ phòng ban hiện tại sang phòng ban khác.
     */
    @McpTool(
            name = "createDocumentAlias",
            description = "Chia sẻ tài liệu (tạo Alias) sang một phòng ban khác mà không cần nhân bản file. Nghiêm cấm chia sẻ tài liệu của Ban Giám đốc (BOARD) ra ngoài."
    )
    public DocumentResponse createDocumentAlias(
            @McpToolParam(description = "Mã UUID của tài liệu gốc cần chia sẻ (lấy từ getDocumentByTitle)") UUID originalDocumentId,
            @McpToolParam(description = "Mã UUID của phòng ban nhận chia sẻ (lấy từ getDepartmentByName)") UUID targetDepartmentId
    ) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        CreateAliasRequest request = new CreateAliasRequest(originalDocumentId, targetDepartmentId);
        return documentService.createAlias(request, currentUser);
    }

    /**
     * Tool: Xem danh sách các tài liệu do phòng ban khác chia sẻ cho phòng ban của người dùng hiện tại.
     */
    @McpTool(
            name = "listSharedDocuments",
            description = "Lấy danh sách các tài liệu liên kết (Alias) mà phòng ban khác chia sẻ cho phòng ban của người dùng hiện tại."
    )
    public List<DocumentResponse> listSharedDocuments(
            @McpToolParam(description = "Số trang (bắt đầu từ 0). Mặc định là 0", required = false) Integer page,
            @McpToolParam(description = "Số lượng bản ghi trên một trang. Mặc định là 10", required = false) Integer size
    ) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        int pageIndex = page != null ? page : 0;
        int pageSize = size != null ? size : 10;
        var pageResult = documentService.listSharedDocuments(pageIndex, pageSize, currentUser);
        return (pageResult != null && pageResult.getContent() != null)
                ? pageResult.getContent()
                : java.util.Collections.emptyList();
    }

    /**
     * Tool: Xem danh sách các phòng ban đang nhận liên kết chia sẻ (Alias) của một tài liệu gốc.
     */
    @McpTool(
            name = "listDocumentAliases",
            description = "Xem danh sách các liên kết chia sẻ (Alias) đang trỏ tới một tài liệu gốc theo mã định danh UUID."
    )
    public List<DocumentResponse> listDocumentAliases(
            @McpToolParam(description = "Mã UUID của tài liệu gốc (lấy từ getDocumentByTitle)") UUID originalDocumentId
    ) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        List<DocumentResponse> list = documentService.listDocumentAliases(originalDocumentId, currentUser);
        return list != null ? list : java.util.Collections.emptyList();
    }

    /**
     * Tool: Thu hồi liên kết chia sẻ tài liệu (xóa Alias) theo mã định danh UUID của Alias.
     */
    @McpTool(
            name = "deleteDocumentAlias",
            description = "Thu hồi liên kết chia sẻ tài liệu (xóa Alias) theo mã định danh UUID của Alias."
    )
    public String deleteDocumentAlias(
            @McpToolParam(description = "Mã UUID của liên kết chia sẻ Alias cần thu hồi") UUID aliasId
    ) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        documentService.deleteAlias(aliasId, currentUser);
        return "Đã thu hồi liên kết chia sẻ tài liệu thành công.";
    }
}

