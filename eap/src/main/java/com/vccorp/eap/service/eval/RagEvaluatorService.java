package com.vccorp.eap.service.eval;

import com.vccorp.eap.dto.RagChatResponse;
import com.vccorp.eap.dto.eval.EvalResultDto;
import com.vccorp.eap.dto.eval.EvalTestCase;
import com.vccorp.eap.dto.eval.RagEvaluationReportDto;

import java.util.List;

/**
 * Service tính toán và đánh giá các chỉ số chất lượng RAG.
 */
public interface RagEvaluatorService {

    /**
     * Đánh giá một test case cụ thể dựa trên câu trả lời và các chunks nhận được.
     */
    EvalResultDto evaluateSingleCase(EvalTestCase testCase, RagChatResponse response);

    /**
     * Tổng hợp danh sách kết quả từng câu hỏi thành báo cáo báo cáo tổng quan.
     */
    RagEvaluationReportDto aggregateReport(List<EvalResultDto> results);
}
