package com.vccorp.eap.service.impl;

import com.vccorp.eap.dto.ChunkResultDto;
import com.vccorp.eap.service.helper.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RagAnswerGeneratorServiceImplTest {

    static class StubLlmClient implements LlmClient {
        String textToReturn;

        @Override
        public Map<String, Object> callLlm(String text, String systemPrompt, long timeoutMs) {
            return Collections.emptyMap();
        }

        @Override
        public String callLlmText(String text, String systemPrompt, long timeoutMs) {
            return textToReturn;
        }
    }

    @Test
    void testGenerateAnswer_EmptyChunks_ReturnsNoAccessMessage() {
        StubLlmClient stubClient = new StubLlmClient();
        RagAnswerGeneratorServiceImpl service = new RagAnswerGeneratorServiceImpl(stubClient);
        String answer = service.generateAnswer("Hỏi về phụ cấp?", Collections.emptyList());
        assertEquals("Rất tiếc, thông tin này không có trong các tài liệu bạn có quyền truy cập.", answer);
    }

    @Test
    void testGenerateAnswer_Success_ReturnsLlmResponseWithCitation() {
        StubLlmClient stubClient = new StubLlmClient();
        stubClient.textToReturn = "Phụ cấp ăn trưa là 500,000 VND. [Quy định lương.pdf, Trang 5]";

        RagAnswerGeneratorServiceImpl service = new RagAnswerGeneratorServiceImpl(stubClient);

        ChunkResultDto chunk = new ChunkResultDto(
            "Phụ cấp ăn trưa là 500,000 VND.",
            0.92,
            "Quy định lương.pdf",
            "DOC-001",
            Map.of("page_number", 5),
            5,
            "[Quy định lương.pdf, Trang 5]"
        );

        String answer = service.generateAnswer("Phụ cấp ăn trưa bao nhiêu?", List.of(chunk));
        assertNotNull(answer);
        assertTrue(answer.contains("[Quy định lương.pdf, Trang 5]"));
        assertTrue(answer.contains("500,000 VND"));
    }

    @Test
    void testGenerateAnswer_LlmFailure_ReturnsFallbackSummary() {
        StubLlmClient stubClient = new StubLlmClient();
        stubClient.textToReturn = null; // simulate LLM failure/timeout

        RagAnswerGeneratorServiceImpl service = new RagAnswerGeneratorServiceImpl(stubClient);

        ChunkResultDto chunk = new ChunkResultDto(
            "Nội dung quy định gửi xe miễn phí.",
            0.88,
            "Nội quy công ty.pdf",
            "DOC-002",
            Map.of("page_number", 2),
            2,
            "[Nội quy công ty.pdf, Trang 2]"
        );

        String answer = service.generateAnswer("Gửi xe thế nào?", List.of(chunk));
        assertNotNull(answer);
        assertTrue(answer.contains("Nội dung quy định gửi xe miễn phí."));
        assertTrue(answer.contains("[Nội quy công ty.pdf, Trang 2]"));
    }
}
