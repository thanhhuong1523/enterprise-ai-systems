package com.vccorp.eap.mcp.orchestrator;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.assistant.AssistantStreamEvent;
import com.vccorp.eap.dto.document.ChunkResultDto;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.infrastructure.security.SecurityContextHelper;
import com.vccorp.eap.mcp.registry.ToolCatalogRegistry;
import com.vccorp.eap.mcp.resilience.ResilienceEngineFacade;
import com.vccorp.eap.mcp.tools.DepartmentTools;
import com.vccorp.eap.mcp.tools.DocumentTools;
import com.vccorp.eap.mcp.validator.FailFastValidator;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.helper.LlmClient;

/**
 * Cài đặt bộ điều phối xâu chuỗi tự chủ (Autonomous Chaining Loop) cho Trợ lý AI.
 * Tuân thủ Single Responsibility: Chỉ điều phối vòng lặp đối thoại, LLM, SSE và uỷ quyền thực thi công cụ cho ToolDispatcher.
 */
@Service
public class AgentOrchestratorImpl implements AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final LlmClient llmClient;
    private final ToolDispatcher toolDispatcher;
    private final ResilienceEngineFacade resilienceEngine;
    private final FailFastValidator failFastValidator;
    private final ToolLoopGuard toolLoopGuard;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor mcpTaskExecutor;
    private final ToolCatalogRegistry toolCatalogRegistry;

    @Value("${eap.mcp.sse-timeout-ms:120000}")
    private final long sseTimeoutMs = 120_000L;

    @Autowired
    public AgentOrchestratorImpl(LlmClient llmClient,
                                 ToolDispatcher toolDispatcher,
                                 ResilienceEngineFacade resilienceEngine,
                                 FailFastValidator failFastValidator,
                                 ToolLoopGuard toolLoopGuard,
                                 ObjectMapper objectMapper,
                                 @Qualifier("mcpTaskExecutor") AsyncTaskExecutor mcpTaskExecutor,
                                 ToolCatalogRegistry toolCatalogRegistry) {
        this.llmClient = llmClient;
        this.toolDispatcher = toolDispatcher;
        this.resilienceEngine = resilienceEngine;
        this.failFastValidator = failFastValidator;
        this.toolLoopGuard = toolLoopGuard;
        this.objectMapper = objectMapper;
        this.mcpTaskExecutor = mcpTaskExecutor;
        this.toolCatalogRegistry = toolCatalogRegistry;
    }

    /**
     * Constructor tương thích ngược cho unit test truyền trực tiếp DepartmentTools và DocumentTools.
     */
    public AgentOrchestratorImpl(LlmClient llmClient,
                                 DepartmentTools departmentTools,
                                 DocumentTools documentTools,
                                 ResilienceEngineFacade resilienceEngine,
                                 FailFastValidator failFastValidator,
                                 ToolLoopGuard toolLoopGuard,
                                 ObjectMapper objectMapper,
                                 AsyncTaskExecutor mcpTaskExecutor,
                                 ToolCatalogRegistry toolCatalogRegistry) {
        this(llmClient,
             new ToolDispatcherImpl(departmentTools, documentTools),
             resilienceEngine,
             failFastValidator,
             toolLoopGuard,
             objectMapper,
             mcpTaskExecutor,
             toolCatalogRegistry);
    }

    @Override
    public SseEmitter streamChat(String userPrompt) {
        SecurityContext context = SecurityContextHolder.getContext();
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        emitter.onCompletion(() -> log.debug("SSE chat stream completed."));
        emitter.onTimeout(() -> {
            log.warn("SSE chat stream timed out sau {}ms.", sseTimeoutMs);
            emitter.complete();
        });
        emitter.onError(e -> log.error("Lỗi trên kênh SSE chat stream: {}", e.getMessage()));

        mcpTaskExecutor.execute(() -> {
            try {
                SecurityContextHolder.setContext(context);
                executeStream(userPrompt, emitter);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    @Override
    public void executeStream(String userPrompt, SseEmitter emitter) {
        User currentUser = SecurityContextHelper.getCurrentUser();

        try {
            // Sự kiện 1: Phân tích ban đầu (thinking)
            sendEvent(emitter, "thinking", AssistantStreamEvent.thinking(0, "Đang phân tích yêu cầu..."));

            String systemPrompt = buildSystemPrompt(currentUser);
            StringBuilder conversationHistory = new StringBuilder();
            conversationHistory.append("User Request: ").append(userPrompt).append("\n");

            List<ChunkResultDto> lastSearchChunks = null;
            int turn = 1;
            while (true) {
                toolLoopGuard.validateTurn(turn);

                String currentPrompt = conversationHistory.toString() +
                        "\nHãy đưa ra bước xử lý tiếp theo bằng JSON format (action là tên tool hoặc FINAL_ANSWER):";

                String rawLlmResponse = llmClient.callLlmText(currentPrompt, systemPrompt, 25000L);
                Map<String, Object> parsed = resilienceEngine.parseAndRecover(rawLlmResponse);

                String thought = parsed.get("thought") != null ? parsed.get("thought").toString().trim() : null;
                if (thought != null && !thought.isEmpty()) {
                    log.info("[AgentOrchestrator][Turn {}][Reasoning] {}", turn, thought);
                    sendEvent(emitter, "reasoning", AssistantStreamEvent.reasoning(turn, thought));
                }

                String action = parsed.get("action") != null ? parsed.get("action").toString() : null;
                if (action == null && parsed.containsKey("tool")) {
                    action = parsed.get("tool").toString();
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> actionInput = (Map<String, Object>) (parsed.get("action_input") instanceof Map
                        ? parsed.get("action_input")
                        : (parsed.get("parameters") instanceof Map ? parsed.get("parameters") : Map.of()));

                // Nếu là FINAL_ANSWER hoặc không có tool call
                if (action == null || "FINAL_ANSWER".equalsIgnoreCase(action)) {
                    String finalAnswer = parsed.get("final_answer") != null
                            ? parsed.get("final_answer").toString()
                            : (parsed.get("text") != null ? parsed.get("text").toString() : "Hoàn tất yêu cầu.");

                    log.info("[AgentOrchestrator][Turn {}][FinalAnswer] {}", turn, finalAnswer);
                    sendEvent(emitter, "content", AssistantStreamEvent.content(turn, finalAnswer, lastSearchChunks));
                    sendEvent(emitter, "done", AssistantStreamEvent.done());
                    emitter.complete();
                    return;
                }

                // Phát hiện tool call -> Tiến hành gọi công cụ
                log.info("[AgentOrchestrator][Turn {}][Action] Calling tool '{}' with input: {}", turn, action, actionInput);
                String startLabel = buildActionStartLabel(action, actionInput);
                sendEvent(emitter, "action_start", AssistantStreamEvent.actionStart(turn, action, startLabel));

                // Thực thi công cụ qua ToolDispatcher (Interface Injection & Single Responsibility)
                Object toolResult = toolDispatcher.executeTool(action, actionInput);
                if (toolResult instanceof RagChatResponse ragResponse) {
                    lastSearchChunks = ragResponse.chunks();
                }

                // Thẩm định Fail-Fast kết quả
                failFastValidator.validateToolExecutionResult(action, toolResult, actionInput);

                String resultJson = serializeToolResult(toolResult);
                log.info("[AgentOrchestrator][Turn {}][Result] Tool '{}' returned: {}", turn, action, resultJson);

                String endLabel = buildActionEndLabel(action, actionInput);
                sendEvent(emitter, "action_end", AssistantStreamEvent.actionEnd(turn, action, "SUCCESS", endLabel));

                // Ghi nhận kết quả vào ngữ cảnh hội thoại cho turn kế tiếp
                conversationHistory.append("\nStep ").append(turn).append(": Action '").append(action)
                        .append("' result: ").append(resultJson).append("\n");

                turn++;
            }

        } catch (BusinessException be) {
            log.warn("BusinessException trong AgentOrchestrator: [{}] {}", be.getErrorCode(), be.getMessage());
            sendEventSafe(emitter, "error", AssistantStreamEvent.error(be.getErrorCode().name(), be.getMessage()));
            sendEventSafe(emitter, "done", AssistantStreamEvent.done());
            emitter.complete();
        } catch (Exception ex) {
            log.error("Lỗi ngoại lệ hệ thống trong AgentOrchestrator: ", ex);
            sendEventSafe(emitter, "error", AssistantStreamEvent.error(ErrorCode.ERR_SYSTEM_ERROR.name(),
                    "Có lỗi xảy ra trong quá trình trợ lý xử lý: " + ex.getMessage()));
            sendEventSafe(emitter, "done", AssistantStreamEvent.done());
            emitter.complete();
        }
    }

    private String buildSystemPrompt(User user) {
        ZonedDateTime nowGmt7 = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String currentTimeStr = nowGmt7.format(TIME_FORMATTER);
        String toolList = toolCatalogRegistry.buildToolListForPrompt();

        return """
                Bạn là Trợ lý AI Tự chủ (Autonomous AI Assistant) của hệ thống VCC Enterprise Archive Platform (EAP).
                Thời gian hiện tại của hệ thống: %s (Múi giờ Asia/Ho_Chi_Minh GMT+7).
                Người dùng hiện tại: %s, Vai trò: %s.
                
                DANH SÁCH CÔNG CỤ ĐƯỢC CẤP QUYỀN TRUY CẬP:
                %s
                
                NGUYÊN TẮC VẬN HÀNH & SUY LUẬN TỰ CHỦ:
                1. Phân tích ý định & bối cảnh:
                   - Đọc kỹ yêu cầu của người dùng cùng lịch sử đối thoại.
                   - Tự đối chiếu mục tiêu của người dùng với mô tả, chức năng và schema của từng công cụ trong danh sách trên để quyết định xem có cần gọi công cụ hay không.
                2. Hướng dẫn phân định ý định & chọn công cụ:
                   - Nhóm 1 (Tra cứu tri thức & thông tin nội bộ):
                     Khi người dùng hỏi bất kỳ thông tin nào cần tra cứu dữ liệu doanh nghiệp (quy định, chính sách, quy chế, nội quy, tài liệu kỹ thuật, thông tin dự án, biên bản, hợp đồng, báo cáo, hướng dẫn quy trình...) -> BẮT BUỘC gọi công cụ `searchDocuments`.
                   - Nhóm 2 (Thực thi tác vụ & hành động hệ thống):
                     Khi người dùng yêu cầu hành động nghiệp vụ (tạo mới, sửa đổi, tra cứu danh sách hoặc cấu hình thực thể...) -> Gọi công cụ hành động tương ứng (ví dụ `createDepartment`, `listDepartments`, `getDepartmentByName`...).
                   - Nhóm 3 (Xâu chuỗi đa tác vụ):
                     Khi yêu cầu đòi hỏi kết hợp nhiều bước (ví dụ vừa tra cứu vừa tạo/sửa) -> Thực hiện từng bước công cụ theo chuỗi logic trước khi tổng kết.
                   - Nhóm 4 (Hội thoại thông thường & Xã giao):
                     Khi người dùng chào hỏi, cảm ơn, hỏi các câu giao tiếp xã giao thông thường không liên quan đến dữ liệu nội bộ -> KHÔNG gọi công cụ, lập tức trả về `FINAL_ANSWER`.
                3. Tự suy luận & trích xuất tham số:
                   - Chỉ gọi công cụ khi cần dữ liệu hoặc cần thực hiện tác vụ nghiệp vụ tương ứng với năng lực của công cụ đó.
                   - Tự suy luận, chuẩn hóa và trích xuất đúng các tham số đầu vào (`action_input`) tuân thủ nghiêm ngặt mô tả và cấu trúc `Input schema` của công cụ được chọn.
                   - Lưu ý kiểm tra vai trò người dùng (ROLE) đối với những hành động yêu cầu quyền quản trị viên.
                4. Đánh giá kết quả & xâu chuỗi:
                   - Sau khi nhận kết quả từ công cụ, tự đánh giá xem đã đủ dữ liệu để trả lời trọn vẹn yêu cầu hay cần gọi thêm công cụ khác để tiếp tục xâu chuỗi thông tin.
                   - Nếu không cần gọi công cụ hoặc khi đã có đủ kết quả xử lý, hãy tổng hợp câu trả lời cuối cùng (`FINAL_ANSWER`).
                5. Trình bày suy luận:
                   - Luôn trình bày tư duy logic, nhận định bối cảnh và lý do lựa chọn hành động trong trường `thought`.
                
                QUY CÁCH PHẢN HỒI (BẮT BUỘC TRẢ VỀ DUY NHẤT 1 CHUỖI JSON HỢP LỆ):
                - Khi quyết định cần gọi công cụ:
                  {
                    "thought": "Trình bày suy luận logic vì sao cần gọi công cụ này và cách chuẩn bị tham số...",
                    "action": "<tên_công_cụ>",
                    "action_input": { ... }
                  }
                - Khi đã đủ thông tin hoặc không cần gọi công cụ:
                  {
                    "thought": "Trình bày suy luận kết luận hoặc lý do đưa ra phản hồi trực tiếp...",
                    "action": "FINAL_ANSWER",
                    "final_answer": "Nội dung phản hồi hoàn chỉnh bằng tiếng Việt rõ ràng, chuyên nghiệp và thân thiện."
                  }
                """.formatted(currentTimeStr, user.getUsername(), user.getRole().name(), toolList);
    }

    private String buildActionStartLabel(String action, Map<String, Object> input) {
        return toolCatalogRegistry.findByName(action)
                .map(info -> toolCatalogRegistry.renderLabel(info.startLabel(), input))
                .orElse("Đang thực hiện công cụ " + action + "...");
    }

    private String buildActionEndLabel(String action, Map<String, Object> input) {
        return toolCatalogRegistry.findByName(action)
                .map(info -> toolCatalogRegistry.renderLabel(info.endLabel(), input))
                .orElse("Hoàn tất thao tác " + action);
    }

    private void sendEvent(SseEmitter emitter, String eventName, AssistantStreamEvent data) throws Exception {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private String serializeToolResult(Object toolResult) {
        try {
            Object toSerialize = (toolResult instanceof java.util.Optional<?> opt) ? opt.orElse(null) : toolResult;
            return objectMapper.writeValueAsString(toSerialize);
        } catch (Exception e) {
            return String.valueOf(toolResult);
        }
    }

    private void sendEventSafe(SseEmitter emitter, String eventName, AssistantStreamEvent data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.debug("Không thể phát sự kiện SSE '{}': {}", eventName, e.getMessage());
        }
    }
}
