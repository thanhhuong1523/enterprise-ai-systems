package com.vccorp.eap.mcp.tools;

import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.mcp.annotation.McpTool;
import com.vccorp.eap.mcp.annotation.McpToolParam;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.search.RetrievalService;
import org.springframework.stereotype.Component;

/**
 * AI Tool Facade cho các nghiệp vụ liên quan đến tra cứu và tìm kiếm tài liệu.
 * Tuân thủ ADR-006.6: Tách biệt hoàn toàn ranh giới AI Facade và Domain Service.
 */
@Component
public class DocumentTools implements McpToolFacade {

    private final RetrievalService retrievalService;

    public DocumentTools(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /**
     * Tool: Tìm kiếm tài liệu, văn bản, quy chế chính sách nội bộ dựa trên câu hỏi hoặc từ khóa tra cứu.
     */
    @McpTool(
            name = "searchDocuments",
            description = "Tìm kiếm và giải đáp thông tin từ kho tri thức và tài liệu nội bộ công ty. "
                    + "Sử dụng công cụ này khi người dùng hỏi về: quy chế, chính sách, hướng dẫn làm việc, "
                    + "tài liệu kỹ thuật, thông tin dự án, báo cáo, biên bản họp, hợp đồng, hoặc bất kỳ câu hỏi "
                    + "nào cần tra cứu dữ liệu nội bộ để trả lời.",
            startLabel = "Đang tra cứu tài liệu '{{query}}'...",
            endLabel = "Đã tra cứu tài liệu thành công"
    )
    public RagChatResponse searchDocuments(
            @McpToolParam(description = "Câu hỏi hoặc từ khóa tra cứu tài liệu") String query
    ) {
        User currentUser = SecurityContextHelper.getCurrentUserOrNull();
        return retrievalService.search(new RagChatRequest(query), currentUser);
    }
}

