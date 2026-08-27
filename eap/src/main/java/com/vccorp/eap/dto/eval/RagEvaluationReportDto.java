package com.vccorp.eap.dto.eval;

import java.util.List;

/**
 * Báo cáo tổng hợp đánh giá chất lượng RAG cho bộ test cases.
 *
 * @param totalTestCases       tổng số câu hỏi test case đã thực thi
 * @param crossDeptLeakRate    tỷ lệ rò rỉ phòng ban (% - SLA: 0.0%)
 * @param citationAccuracyRate tỷ lệ chính xác trích dẫn (% - SLA: 100.0%)
 * @param avgContextRelevance  điểm trung bình độ tương quan bối cảnh (0.0 -> 1.0)
 * @param avgFaithfulness     điểm trung bình độ trung thực (0.0 -> 1.0)
 * @param detailsList          danh sách chi tiết kết quả từng test case
 */
public record RagEvaluationReportDto(
    int totalTestCases,
    double crossDeptLeakRate,
    double citationAccuracyRate,
    double avgContextRelevance,
    double avgFaithfulness,
    List<EvalResultDto> detailsList
) {}
