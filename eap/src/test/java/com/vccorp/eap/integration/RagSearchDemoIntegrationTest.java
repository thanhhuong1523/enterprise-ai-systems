package com.vccorp.eap.integration;

import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.dto.document.ChunkResultDto;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Chunk;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.service.document.LlmMetadataExtractorService;
import com.vccorp.eap.service.search.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.vccorp.eap.service.embedding.EmbeddingService;
import com.vccorp.eap.service.helper.LlmClient;

@SpringBootTest
@Transactional
public class RagSearchDemoIntegrationTest {

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private com.vccorp.eap.repository.DocumentRepository documentRepository;

    @Autowired
    private com.vccorp.eap.service.embedding.EmbeddingService embeddingService;

    @MockBean
    private LlmMetadataExtractorService llmMetadataExtractorService;

    @MockBean
    private com.vccorp.eap.service.helper.LlmClient llmClient;

    @Test
    public void testRagIngestionAndSimilaritySearch() {
        // 1. Ghi khánh thành tài liệu giả lập vào database
        UUID docId = UUID.randomUUID();
        UUID chunk1Id = UUID.randomUUID();
        UUID chunk2Id = UUID.randomUUID();

        // Lưu document cha trước để tránh lỗi khóa ngoại
        com.vccorp.eap.model.Document parentDoc = com.vccorp.eap.model.Document.builder()
            .id(docId)
            .businessCode("RAG-" + docId.toString().substring(0, 8))
            .title("Test Document")
            .fileReference("mock-file-ref")
            .ownerDepartmentId(UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1")) // Ban Giám Đốc
            .createdAt(java.time.LocalDateTime.now())
            .status("COMPLETED")
            .retryCount(0)
            .totalChunks(2)
            .lastCompletedChunk(2)
            .build();
        documentRepository.saveAndFlush(parentDoc);

        String text1 = "Học sinh, sinh viên thực tập tại VCCorp được phụ cấp ăn trưa và gửi xe miễn phí.";
        String text2 = "Quy định đi làm muộn tại công ty VCCorp: nhân viên phải đến trước 9 giờ sáng hàng ngày.";

        Chunk c1 = new Chunk(
            chunk1Id,
            docId,
            0,
            text1,
            embeddingService.embedText(text1),
            new HashMap<>()
        );

        Chunk c2 = new Chunk(
            chunk2Id,
            docId,
            1,
            text2,
            embeddingService.embedText(text2),
            new HashMap<>()
        );

        // Lưu vào vector store thông qua repository và flush để visible với JdbcTemplate
        chunkRepository.saveAllAndFlush(List.of(c1, c2));

        // Kiểm tra xem dữ liệu đã được lưu thành công vào bảng tbl_chunks chưa
        Optional<Chunk> chunk1Opt = chunkRepository.findById(chunk1Id);
        Optional<Chunk> chunk2Opt = chunkRepository.findById(chunk2Id);

        assertTrue(chunk1Opt.isPresent());
        assertTrue(chunk2Opt.isPresent());
        assertEquals("Học sinh, sinh viên thực tập tại VCCorp được phụ cấp ăn trưa và gửi xe miễn phí.", chunk1Opt.get().getContent());

        // 2. Giả lập người dùng hỏi: "Thực tập sinh có được gửi xe không?"
        String question = "Thực tập sinh có được gửi xe không?";
        
        // Mock LLM trích xuất metadata trả về map rỗng để chạy tìm kiếm tương đồng vector bình thường
        when(llmMetadataExtractorService.extractMetadata(eq(question))).thenReturn(Collections.emptyMap());
        when(llmClient.callLlmText(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("Học sinh, sinh viên thực tập tại VCCorp được phụ cấp ăn trưa và gửi xe miễn phí. [Test Document]");

        // 3. Thực hiện truy vấn tương đồng vector thông qua RetrievalService
        User testUser = User.builder()
            .id(UUID.randomUUID())
            .username("test_user_rag")
            .email("test_user_rag@vccorp.vn")
            .passwordHash("password")
            .role(Role.ROLE_EMPLOYEE)
            .departmentId(UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1"))
            .build();
        RagChatResponse response = retrievalService.search(new RagChatRequest(question), testUser);

        // 4. Xác nhận kết quả
        assertNotNull(response);
        assertNotNull(response.response());
        assertNotNull(response.chunks());
        
        List<ChunkResultDto> resultChunks = response.chunks();
        assertFalse(resultChunks.isEmpty());
        assertEquals(text1, resultChunks.get(0).content());
    }

    @Test
    public void testRagSearchFallbackWhenLlmFailsOrNotConfigured() {
        // 1. Ghi tài liệu giả lập
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        com.vccorp.eap.model.Document parentDoc = com.vccorp.eap.model.Document.builder()
            .id(docId)
            .businessCode("RAG-FALLBACK-" + docId.toString().substring(0, 8))
            .title("Fallback Test Document")
            .fileReference("mock-fallback-file-ref")
            .ownerDepartmentId(UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1")) // Ban Giám Đốc
            .createdAt(java.time.LocalDateTime.now())
            .status("COMPLETED")
            .retryCount(0)
            .totalChunks(1)
            .lastCompletedChunk(1)
            .build();
        documentRepository.saveAndFlush(parentDoc);

        String text = "Quy định hỗ trợ tiền ăn trưa là 500,000 VND một tháng.";
        Chunk c = new Chunk(
            chunkId,
            docId,
            0,
            text,
            embeddingService.embedText(text),
            new HashMap<>()
        );
        chunkRepository.saveAndFlush(c);

        // Giả lập LLM ném ngoại lệ (lỗi API hoặc không có API key)
        String question = "Tiền ăn trưa được hỗ trợ bao nhiêu?";
        when(llmMetadataExtractorService.extractMetadata(eq(question))).thenThrow(new RuntimeException("Gemini API error"));

        User testUser = User.builder()
            .id(UUID.randomUUID())
            .username("test_user_fallback")
            .email("test_user_fallback@vccorp.vn")
            .passwordHash("password")
            .role(Role.ROLE_EMPLOYEE)
            .departmentId(UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1"))
            .build();

        // Thực hiện tìm kiếm - hệ thống sẽ bắt ngoại lệ và chạy fallback vector search trực tiếp
        RagChatResponse response = retrievalService.search(new RagChatRequest(question), testUser);

        // Xác nhận kết quả fallback vẫn lấy được dữ liệu phù hợp
        assertNotNull(response);
        List<ChunkResultDto> resultChunks = response.chunks();
        assertFalse(resultChunks.isEmpty());
        assertEquals(text, resultChunks.get(0).content());
    }

    @Test
    public void testRagSearchSimilarityThresholdFiltering() {
        // 1. Ghi tài liệu giả lập
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        com.vccorp.eap.model.Document parentDoc = com.vccorp.eap.model.Document.builder()
            .id(docId)
            .businessCode("RAG-THRES-" + docId.toString().substring(0, 8))
            .title("Threshold Test Document")
            .fileReference("mock-threshold-file-ref")
            .ownerDepartmentId(UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1"))
            .createdAt(java.time.LocalDateTime.now())
            .status("COMPLETED")
            .retryCount(0)
            .totalChunks(1)
            .lastCompletedChunk(1)
            .build();
        documentRepository.saveAndFlush(parentDoc);

        String text = "Quy định này áp dụng từ ngày 01/01/2026.";
        Chunk c = new Chunk(
            chunkId,
            docId,
            0,
            text,
            embeddingService.embedText(text),
            new HashMap<>()
        );
        chunkRepository.saveAndFlush(c);

        String question = "Quy định áp dụng khi nào?";
        when(llmMetadataExtractorService.extractMetadata(eq(question))).thenReturn(Collections.emptyMap());

        User testUser = User.builder()
            .id(UUID.randomUUID())
            .username("test_user_threshold")
            .email("test_user_threshold@vccorp.vn")
            .passwordHash("password")
            .role(Role.ROLE_EMPLOYEE)
            .departmentId(UUID.fromString("d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1"))
            .build();

        // Đặt similarityThreshold lên rất cao (ví dụ: 0.99) để chắc chắn kết quả tìm thấy bị lọc bỏ
        org.springframework.test.util.ReflectionTestUtils.setField(retrievalService, "similarityThreshold", 0.99);

        try {
            RagChatResponse response = retrievalService.search(new RagChatRequest(question), testUser);
            assertNotNull(response);
            assertEquals("Rất tiếc, thông tin này không có trong các tài liệu bạn có quyền truy cập.", response.response());
            assertTrue(response.chunks().isEmpty());
        } finally {
            // Revert threshold lại 0.0 để không ảnh hưởng các test khác
            org.springframework.test.util.ReflectionTestUtils.setField(retrievalService, "similarityThreshold", 0.0);
        }
    }
}
