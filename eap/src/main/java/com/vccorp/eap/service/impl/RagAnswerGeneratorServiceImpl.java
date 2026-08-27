package com.vccorp.eap.service.impl;

import com.vccorp.eap.dto.ChunkResultDto;
import com.vccorp.eap.service.RagAnswerGeneratorService;
import com.vccorp.eap.service.helper.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagAnswerGeneratorServiceImpl implements RagAnswerGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(RagAnswerGeneratorServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
        Bạn là trợ lý AI tìm kiếm tri thức doanh nghiệp của VCCorp.
        Nhiệm vụ của bạn là tổng hợp và trả lời câu hỏi của người dùng dựa TRỰC TIẾP và DUY NHẤT vào danh sách các đoạn tài liệu tham khảo (Context) được cung cấp.

        QUY TẮC BẮT BUỘC:
        1. Mỗi khi bạn đưa ra một thông tin hoặc sự thật trích từ Context, bạn BẮT BUỘC phải đính kèm ngay trích dẫn nguồn theo đúng định dạng `[Tên_tài_liệu, Trang X]` (hoặc `[Tên_tài_liệu]` nếu không có số trang) tương ứng với đoạn Context đó.
        2. Tuyệt đối không tự bịa đặt, không đưa ra thông tin không có trong Context.
        3. Nếu Context được cung cấp không chứa đủ thông tin để trả lời câu hỏi, hãy phản hồi: "Rất tiếc, thông tin này không có trong các tài liệu bạn có quyền truy cập."
        4. Trả lời bằng tiếng Việt, trình bày mạch lạc, ngắn gọn và chính xác.
        """;

    private final LlmClient llmClient;

    @Value("${eap.rag.generation-timeout-ms:15000}")
    private long generationTimeoutMs;

    public RagAnswerGeneratorServiceImpl(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String generateAnswer(String query, List<ChunkResultDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "Rất tiếc, thông tin này không có trong các tài liệu bạn có quyền truy cập.";
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkResultDto chunk = chunks.get(i);
            contextBuilder.append(String.format("--- ĐOẠN TÀI LIỆU %d ---\n", i + 1));
            contextBuilder.append(String.format("Nguồn trích dẫn chuẩn: %s\n", chunk.citation()));
            contextBuilder.append(String.format("Tên tài liệu: %s\n", chunk.documentTitle()));
            if (chunk.pageNumber() != null) {
                contextBuilder.append(String.format("Trang: %d\n", chunk.pageNumber()));
            }
            contextBuilder.append(String.format("Nội dung:\n%s\n\n", chunk.content()));
        }

        String userContent = String.format("""
            DANH SÁCH CONTEXT THAM KHẢO:
            %s

            CÂU HỎI CỦA NGƯỜI DÙNG:
            %s
            """, contextBuilder.toString(), query);

        log.info("Gửi request tới LLM để sinh câu trả lời RAG kèm trích dẫn cho câu hỏi: '{}'", query);
        String generatedAnswer = llmClient.callLlmText(userContent, SYSTEM_PROMPT, generationTimeoutMs);

        if (generatedAnswer != null && !generatedAnswer.isBlank()) {
            log.info("LLM đã sinh câu trả lời thành công.");
            return generatedAnswer.trim();
        }

        log.warn("LLM không trả về kết quả sinh văn bản hoặc bị timeout. Sử dụng fallback response.");
        StringBuilder fallbackBuilder = new StringBuilder("Dưới đây là các đoạn thông tin liên quan được tìm thấy:\n");
        for (ChunkResultDto chunk : chunks) {
            fallbackBuilder.append(String.format("- %s %s\n", chunk.content(), chunk.citation()));
        }
        return fallbackBuilder.toString();
    }
}
