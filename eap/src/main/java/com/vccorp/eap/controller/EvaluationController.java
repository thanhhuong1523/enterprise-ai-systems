package com.vccorp.eap.controller;

import com.vccorp.eap.common.response.ApiResponse;
import com.vccorp.eap.dto.eval.RagEvaluationReportDto;
import com.vccorp.eap.service.eval.EvaluationHarnessRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cung cấp API kích hoạt chạy hệ thống đánh giá Evaluation Harness.
 */
@RestController
@RequestMapping("/api/v1/eval")
public class EvaluationController {

    private final EvaluationHarnessRunner evaluationHarnessRunner;

    public EvaluationController(EvaluationHarnessRunner evaluationHarnessRunner) {
        this.evaluationHarnessRunner = evaluationHarnessRunner;
    }

    /**
     * Trigger chạy đánh giá 20 test cases và trả về báo cáo JSON chi tiết.
     */
    @GetMapping("/run")
    @PreAuthorize("hasRole('DEPT_MANAGER')")
    public ApiResponse<RagEvaluationReportDto> runEvaluation() {
        RagEvaluationReportDto report = evaluationHarnessRunner.runEvaluation();
        return ApiResponse.success(report);
    }
}
