package com.vccorp.eap.controller.search;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.search.RetrievalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint tìm kiếm ngữ nghĩa có phân quyền phòng ban.
 * Controller chỉ nhận request và trả về response — mọi logic nằm trong RetrievalService.
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/search")
    public ApiResponse<RagChatResponse> search(
            @Valid @RequestBody RagChatRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(retrievalService.search(request, currentUser));
    }
}
