package com.vccorp.eap.service.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.*;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    private final LlmMetadataExtractorService llmMetadataExtractorService;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final TaskExecutor taskExecutor;
    private final DepartmentRepository departmentRepository;
    private final ChunkRepository chunkRepository;
    private final RagAnswerGeneratorService ragAnswerGeneratorService;

    public RetrievalServiceImpl(
            LlmMetadataExtractorService llmMetadataExtractorService,
            EmbeddingService embeddingService,
            VectorSearchService vectorSearchService,
            @Qualifier("asyncRetrievalExecutor") TaskExecutor taskExecutor,
            DepartmentRepository departmentRepository,
            ChunkRepository chunkRepository,
            RagAnswerGeneratorService ragAnswerGeneratorService) {
        this.llmMetadataExtractorService = llmMetadataExtractorService;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.taskExecutor = taskExecutor;
        this.departmentRepository = departmentRepository;
        this.chunkRepository = chunkRepository;
        this.ragAnswerGeneratorService = ragAnswerGeneratorService;
    }

    @Override
    public RagChatResponse search(RagChatRequest request, User currentUser) {
        if (request == null || request.message() == null || request.message().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Tin nhắn không được để trống.");
        }

        // 1. Phân quyền: Block SYSTEM_ADMIN
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE,
                    "Quản trị viên hệ thống không được phép tìm kiếm tài liệu.");
        }

        String message = request.message();

        // 2. Chạy song song CompletableFuture: Lấy LLM metadata + embed câu hỏi
        CompletableFuture<Map<String, Object>> llmFuture = CompletableFuture.supplyAsync(
                () -> llmMetadataExtractorService.extractMetadata(message), taskExecutor)
                .exceptionally(ex -> {
                    log.warn("Lấy LLM metadata thất bại, tự động bỏ qua bộ lọc: {}", ex.getMessage());
                    return Collections.emptyMap();
                });
        
        CompletableFuture<float[]> embedFuture = CompletableFuture.supplyAsync(
                () -> embeddingService.embedText(message), taskExecutor);

        // Chờ cả 2 kết thúc
        CompletableFuture.allOf(llmFuture, embedFuture).join();

        Map<String, Object> metadataFilter = llmFuture.getNow(Collections.emptyMap());
        log.info("Kết quả trích xuất metadata từ LLM cho câu hỏi '{}': {}", message, metadataFilter);
        float[] queryVector = embedFuture.getNow(null);

        UUID boardDeptId = departmentRepository.findByCode("BOARD")
                .map(d -> d.getId())
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        // 3. Short-circuit: 
        // a) Nếu LLM không trích xuất được value metadata nào từ câu hỏi -> Trả về không tìm thấy ngay.
        // b) Nếu trích xuất được metadata nhưng không có ứng viên nào khớp trong DB -> Trả về không tìm thấy ngay.
        if (metadataFilter == null || metadataFilter.isEmpty()) {
            log.info("Short-circuit: không trích xuất được metadata filter nào từ câu hỏi cho user {}",
                    currentUser.getUsername());
            return new RagChatResponse("Không tìm thấy kết quả phù hợp.", Collections.emptyList());
        }

        boolean hasCandidate = chunkRepository.existsCandidateWithMetadata(
                metadataFilter, currentUser.getDepartmentId(), boardDeptId);
        if (!hasCandidate) {
            log.info("Short-circuit: không có phân đoạn nào khớp metadata filter cho user {}",
                    currentUser.getUsername());
            return new RagChatResponse("Không tìm thấy kết quả phù hợp.", Collections.emptyList());
        }

        // 4. Đóng gói context và tìm kiếm tại Database (sau khi LLM filter, xếp hạng theo vector cosine)
        SearchContext context = new SearchContext(message, queryVector, metadataFilter, currentUser.getDepartmentId(), boardDeptId);
        List<ChunkSearchResult> searchResults = vectorSearchService.searchWithAuth(context);

        // 5. Map sang typed DTO cho API Response (không áp dụng threshold điểm cosine)
        List<ChunkResultDto> chunks = searchResults.stream()
                .map(res -> {
                    Object rawPage = res.metadata().get("page_number");
                    Integer pageNumber = null;
                    if (rawPage instanceof Integer i) {
                        pageNumber = i;
                    } else if (rawPage instanceof Number n) {
                        pageNumber = n.intValue();
                    }
                    String citation = "[" + res.displayTitle()
                            + (pageNumber != null ? ", Trang " + pageNumber : "")
                            + "]";
                    return new ChunkResultDto(
                            res.content(),
                            res.similarityScore(),
                            res.displayTitle(),
                            res.businessCode(),
                            res.metadata(),
                            pageNumber,
                            citation
                    );
                }).collect(Collectors.toList());

        if (chunks.isEmpty()) {
            log.info("Không tìm thấy kết quả nào cho user {}: {}", currentUser.getUsername(), message);
            return new RagChatResponse("Rất tiếc, thông tin này không có trong các tài liệu bạn có quyền truy cập.", Collections.emptyList());
        }

        log.info("Tìm thấy {} kết quả cho user {}: {}. Đang gọi LLM tổng hợp câu trả lời...", chunks.size(), currentUser.getUsername(), message);
        String generatedResponse = ragAnswerGeneratorService.generateAnswer(message, chunks);
        return new RagChatResponse(generatedResponse, chunks);
    }
}
