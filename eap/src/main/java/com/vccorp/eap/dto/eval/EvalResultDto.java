package com.vccorp.eap.dto.eval;

import java.util.List;

/**
 * Record chứa kết quả đánh giá cho một test case cụ thể.
 *
 * @param testCaseId            mã test case
 * @param question              nội dung câu hỏi
 * @param crossDeptLeak         có bị rò rỉ dữ liệu phòng ban khác không (true = vi phạm security)
 * @param hasCitation           câu trả lời/chunks có chứa trích dẫn hay không
 * @param citationCorrect       trích dẫn có chính xác khớp tên tài liệu và số trang kỳ vọng
 * @param contextRelevanceScore điểm số độ tương quan bối cảnh (0.0 -> 1.0)
 * @param faithfulnessScore     điểm số độ trung thực so với context (0.0 -> 1.0)
 * @param actualAnswer          câu trả lời thực tế từ RAG
 * @param retrievedCitations    danh sách trích dẫn thu thập được từ Chunks trả về
 */
public record EvalResultDto(
    String testCaseId,
    String question,
    boolean crossDeptLeak,
    boolean hasCitation,
    boolean citationCorrect,
    double contextRelevanceScore,
    double faithfulnessScore,
    String actualAnswer,
    List<String> retrievedCitations
) {}
