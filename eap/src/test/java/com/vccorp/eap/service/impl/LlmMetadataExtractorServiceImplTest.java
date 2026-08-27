package com.vccorp.eap.service.impl;

import com.vccorp.eap.service.helper.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class LlmMetadataExtractorServiceImplTest {

    static class StubLlmClient implements LlmClient {
        Map<String, Object> responseToReturn;

        @Override
        public Map<String, Object> callLlm(String text, String systemPrompt, long timeoutMs) {
            return responseToReturn;
        }

        @Override
        public String callLlmText(String text, String systemPrompt, long timeoutMs) {
            return null;
        }
    }

    @Test
    public void testExtractMetadataNormalizesToLowercaseAndOmitsKeywords() {
        StubLlmClient stubClient = new StubLlmClient();
        Map<String, Object> mockLlmOutput = new HashMap<>();
        mockLlmOutput.put("doc_type", "GUIDE");
        mockLlmOutput.put("topics", List.of("HR_POLICY", "Compensation_Benefits"));
        mockLlmOutput.put("entities", List.of("DEPT:Phòng Nhân Sự", "PERSON:Nguyễn Văn A"));
        mockLlmOutput.put("time_refs", List.of("Năm 2026"));
        mockLlmOutput.put("keywords", List.of("xin nghỉ thai sản", "thực tập sinh"));
        stubClient.responseToReturn = mockLlmOutput;

        LlmMetadataExtractorServiceImpl extractorService = new LlmMetadataExtractorServiceImpl(stubClient, 5000L, 40000L);

        Map<String, Object> result = extractorService.extractMetadata("Quy trình xin nghỉ thai sản của phòng Nhân sự?");

        assertNotNull(result);
        assertEquals("guide", result.get("doc_type"));
        assertEquals(List.of("hr_policy", "compensation_benefits"), result.get("topics"));
        assertEquals(List.of("dept:phòng nhân sự", "person:nguyễn văn a"), result.get("entities"));
        assertEquals(List.of("năm 2026"), result.get("time_refs"));

        assertFalse(result.containsKey("keywords"), "Query filter metadata must NOT contain keywords");
    }

    @Test
    public void testExtractMetadataOmitsDocTypeAndTopicsWhenUnspecified() {
        StubLlmClient stubClient = new StubLlmClient();
        Map<String, Object> mockLlmOutput = new HashMap<>();
        mockLlmOutput.put("doc_type", "other");
        mockLlmOutput.put("topics", List.of("general_info"));
        mockLlmOutput.put("entities", List.of("LOC:Bái Đính", "CONCEPT:Danh Lam Thắng Cảnh"));
        stubClient.responseToReturn = mockLlmOutput;

        LlmMetadataExtractorServiceImpl extractorService = new LlmMetadataExtractorServiceImpl(stubClient, 5000L, 40000L);
        Map<String, Object> result = extractorService.extractMetadata("Thông tin danh lam thắng cảnh Bái Đính");

        assertNotNull(result);
        assertFalse(result.containsKey("doc_type"), "doc_type should be omitted if 'other'");
        assertFalse(result.containsKey("topics"), "topics should be omitted if 'general_info'");
        assertEquals(List.of("loc:bái đính", "concept:danh lam thắng cảnh"), result.get("entities"));
    }

    @Test
    public void testExtractChunkMetadataNormalizesAllValuesToLowercaseAndDetailedEntities() {
        StubLlmClient stubClient = new StubLlmClient();
        Map<String, Object> mockLlmOutput = new HashMap<>();
        mockLlmOutput.put("doc_type", "ANALYSIS");
        mockLlmOutput.put("topics", List.of("FINANCE_ACCOUNTING"));
        mockLlmOutput.put("entities", List.of("ORG:VCCorp", "LOC:Hà Nội", "CONCEPT:Báo Cáo Tài Chính", "PERSON:Trưởng Phòng"));
        mockLlmOutput.put("time_refs", List.of("Quý 1"));
        mockLlmOutput.put("citation_headings", List.of("Chương 1: TỔNG QUAN", "Mục 1.1: BÁO CÁO"));
        stubClient.responseToReturn = mockLlmOutput;

        LlmMetadataExtractorServiceImpl extractorService = new LlmMetadataExtractorServiceImpl(stubClient, 5000L, 40000L);

        Map<String, Object> result = extractorService.extractChunkMetadata("Nội dung báo cáo tài chính VCCorp Hà Nội", "Tiêu đề 1");

        assertNotNull(result);
        assertEquals("analysis", result.get("doc_type"));
        assertEquals(List.of("finance_accounting"), result.get("topics"));
        assertEquals(List.of("org:vccorp", "loc:hà nội", "concept:báo cáo tài chính", "person:trưởng phòng"), result.get("entities"));
        assertEquals(List.of("quý 1"), result.get("time_refs"));
        assertFalse(result.containsKey("keywords"), "Chunk metadata must NOT contain keywords");
        assertEquals(List.of("Chương 1: TỔNG QUAN", "Mục 1.1: BÁO CÁO"), result.get("citation_headings"), "citation_headings must preserve original casing");
    }

    @Test
    public void testExtractChunkMetadataIncludesHeadingContextInSystemPrompt() {
        final String[] capturedSystemPrompt = new String[1];
        LlmClient capturingClient = new StubLlmClient() {
            @Override
            public Map<String, Object> callLlm(String text, String systemPrompt, long timeoutMs) {
                capturedSystemPrompt[0] = systemPrompt;
                return Map.of("doc_type", "regulation", "topics", List.of("compensation_benefits"));
            }
        };

        LlmMetadataExtractorServiceImpl extractorService = new LlmMetadataExtractorServiceImpl(capturingClient, 5000L, 40000L);
        extractorService.extractChunkMetadata("Nội dung hỗ trợ gửi xe", "Chương II: Phụ cấp năm 2026 > Mục 1: Gửi xe");

        assertNotNull(capturedSystemPrompt[0]);
        assertTrue(capturedSystemPrompt[0].contains("BỐI CẢNH TIÊU ĐỀ (Citation Headings Stack): Chương II: Phụ cấp năm 2026 > Mục 1: Gửi xe"));
        assertTrue(capturedSystemPrompt[0].contains("TRÍCH XUẤT ĐẦY ĐỦ TỪ ĐỀ MỤC PHÂN CẤP"));
    }
}
