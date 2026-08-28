package com.vccorp.eap.service.search.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.document.ChunkResultDto;
import com.vccorp.eap.dto.document.ChunkSearchResult;
import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.dto.search.SearchContext;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.document.LlmMetadataExtractorService;
import com.vccorp.eap.service.embedding.EmbeddingService;
import com.vccorp.eap.service.helper.LlmClient;
import com.vccorp.eap.service.mcp.JsonSelfCorrectionService;
import com.vccorp.eap.service.mcp.McpSchemaEngine;
import com.vccorp.eap.service.mcp.McpToolExecutor;
import com.vccorp.eap.service.search.RagAnswerGeneratorService;
import com.vccorp.eap.service.search.RetrievalService;
import com.vccorp.eap.service.search.VectorSearchService;

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
    private final McpSchemaEngine mcpSchemaEngine;
    private final McpToolExecutor mcpToolExecutor;
    private final JsonSelfCorrectionService jsonSelfCorrectionService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${eap.rag.similarity-threshold:0.0}")
    private double similarityThreshold;

    public RetrievalServiceImpl(
            LlmMetadataExtractorService llmMetadataExtractorService,
            EmbeddingService embeddingService,
            VectorSearchService vectorSearchService,
            @Qualifier("asyncRetrievalExecutor") TaskExecutor taskExecutor,
            DepartmentRepository departmentRepository,
            ChunkRepository chunkRepository,
            RagAnswerGeneratorService ragAnswerGeneratorService,
            McpSchemaEngine mcpSchemaEngine,
            McpToolExecutor mcpToolExecutor,
            JsonSelfCorrectionService jsonSelfCorrectionService,
            LlmClient llmClient,
            ObjectMapper objectMapper) {
        this.llmMetadataExtractorService = llmMetadataExtractorService;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.taskExecutor = taskExecutor;
        this.departmentRepository = departmentRepository;
        this.chunkRepository = chunkRepository;
        this.ragAnswerGeneratorService = ragAnswerGeneratorService;
        this.mcpSchemaEngine = mcpSchemaEngine;
        this.mcpToolExecutor = mcpToolExecutor;
        this.jsonSelfCorrectionService = jsonSelfCorrectionService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagChatResponse search(RagChatRequest request, User currentUser) {
        if (request == null || request.message() == null || request.message().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Tin nhắn không được để trống.");
        }

        String message = request.message().trim();

        // Check if user is requesting a tool call (Agent Route)
        RagChatResponse toolCallResponse = tryExecuteAgentToolCall(message, currentUser);
        if (toolCallResponse != null) {
            return toolCallResponse;
        }

        // 1. Phân quyền: Block SYSTEM_ADMIN
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE,
                    "Quản trị viên hệ thống không được phép tìm kiếm tài liệu.");
        }

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
        // Nếu trích xuất được metadata nhưng không có ứng viên nào khớp trong DB -> Trả về không tìm thấy ngay.
        if (metadataFilter != null && !metadataFilter.isEmpty()) {
            boolean hasCandidate = chunkRepository.existsCandidateWithMetadata(
                    metadataFilter, currentUser.getDepartmentId(), boardDeptId);
            if (!hasCandidate) {
                log.info("Short-circuit: không có phân đoạn nào khớp metadata filter cho user {}",
                        currentUser.getUsername());
                return new RagChatResponse("Không tìm thấy kết quả phù hợp.", Collections.emptyList());
            }
        }

        // 4. Đóng gói context và tìm kiếm tại Database (sau khi LLM filter, xếp hạng theo vector cosine)
        SearchContext context = new SearchContext(message, queryVector, metadataFilter, currentUser.getDepartmentId(), boardDeptId);
        List<ChunkSearchResult> searchResults = vectorSearchService.searchWithAuth(context);

        // 5. Map sang typed DTO cho API Response (áp dụng threshold điểm cosine)
        List<ChunkResultDto> chunks = searchResults.stream()
                .filter(res -> res.similarityScore() >= similarityThreshold)
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

    private RagChatResponse tryExecuteAgentToolCall(String message, User currentUser) {
        try {
            // 1. Get available tools filtered by user's role
            List<Map<String, Object>> tools = mcpSchemaEngine.getAvailableToolsForUser(currentUser);
            if (tools == null || tools.isEmpty()) {
                return null;
            }

            // 2. Build system prompt for Agent Tool Router
            String toolsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tools);
            String systemPrompt = "Bạn là trợ lý ảo AI điều phối công cụ (Agent Tool Router) của doanh nghiệp.\n" +
                    "Nhiệm vụ: Phân tích câu lệnh của người dùng và quyết định xem câu lệnh đó có yêu cầu gọi một công cụ (tool) nào dưới đây hay không.\n\n" +
                    "## DANH SÁCH CÔNG CỤ HIỆN CÓ:\n" +
                    toolsJson + "\n\n" +
                    "## QUY TẮC ĐẦU RA:\n" +
                    "- Trả về DUY NHẤT một đối tượng JSON có cấu trúc như sau:\n" +
                    "  Nếu cần gọi công cụ:\n" +
                    "  {\n" +
                    "    \"is_tool_call\": true,\n" +
                    "    \"tool_name\": \"tên_công_cụ\",\n" +
                    "    \"arguments\": { ... các tham số trích xuất tương ứng ... }\n" +
                    "  }\n" +
                    "  Nếu KHÔNG cần gọi công cụ (chỉ là câu hỏi bình thường, chào hỏi, hoặc tìm kiếm tài liệu):\n" +
                    "  {\n" +
                    "    \"is_tool_call\": false\n" +
                    "  }\n\n" +
                    "- Tuyệt đối không giải thích, không markdown, không bọc trong thẻ ```json.";

            // 3. Inject system timestamp context for relative time extraction
            String userContent = "Thời gian hệ thống hiện tại: " + java.time.LocalDateTime.now() + "\n" +
                    "Câu lệnh của người dùng: " + message;

            // 4. Call LLM to decide
            String llmResponse = llmClient.callLlmText(userContent, systemPrompt, 10000);
            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                return null;
            }

            // 5. Correct and parse JSON using JsonSelfCorrectionService
            Map<String, Object> responseMap = jsonSelfCorrectionService.validateAndCorrect(llmResponse);
            
            Boolean isToolCall = (Boolean) responseMap.get("is_tool_call");
            if (isToolCall != null && isToolCall) {
                String toolName = (String) responseMap.get("tool_name");
                Map<String, Object> arguments = (Map<String, Object>) responseMap.get("arguments");
                if (arguments == null) {
                    arguments = new HashMap<>();
                }

                log.info("Agent Tool Router nhận định người dùng {} muốn gọi công cụ: {} với tham số: {}", 
                        currentUser.getUsername(), toolName, arguments);

                // 6. Execute the tool call
                String rawResult = mcpToolExecutor.executeToolCall(toolName, arguments);
                
                // 7. Format natural language response using LLM
                String summarizeSystemPrompt = "Bạn là trợ lý ảo AI thân thiện. Hãy dịch kết quả thực thi API (dạng JSON) của công cụ sau đây thành một câu trả lời tự nhiên, lịch sự và rõ ràng bằng tiếng Việt cho người dùng.";
                String summarizeUserContent = String.format("Yêu cầu của người dùng: %s\nCông cụ đã gọi: %s\nKết quả API trả về:\n%s",
                        message, toolName, rawResult);
                
                String naturalLanguageResponse = llmClient.callLlmText(summarizeUserContent, summarizeSystemPrompt, 8000);
                if (naturalLanguageResponse == null || naturalLanguageResponse.trim().isEmpty()) {
                    naturalLanguageResponse = "Đã thực thi công cụ " + toolName + " thành công. Kết quả: " + rawResult;
                }

                return new RagChatResponse(naturalLanguageResponse, Collections.emptyList());
            }
        } catch (Exception e) {
            log.error("Lỗi khi xử lý Tool Call Agent: {}", e.getMessage(), e);
            // In case of error (e.g. invalid json format), fallback to RAG instead of crashing
        }
        return null;
    }
}
