package com.vccorp.eap.service.search;

import com.vccorp.eap.dto.document.ChunkResultDto;
import java.util.List;

public interface RagAnswerGeneratorService {

    /**
     * Dựa trên danh sách các chunks được truy vấn, gọi LLM để tổng hợp câu trả lời tự nhiên
     * có đính kèm trích dẫn chính xác nguồn gốc (tên file, số trang PDF).
     *
     * @param query  câu hỏi của người dùng
     * @param chunks danh sách đoạn văn bản trích xuất được từ vector search
     * @return câu trả lời hoàn chỉnh kèm inline citations
     */
    String generateAnswer(String query, List<ChunkResultDto> chunks);
}
