package com.vccorp.eap.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.dto.RagChatRequest;
import com.vccorp.eap.dto.RagChatResponse;
import com.vccorp.eap.dto.eval.EvalResultDto;
import com.vccorp.eap.dto.eval.EvalTestCase;
import com.vccorp.eap.dto.eval.RagEvaluationReportDto;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runner thực thi bộ kiểm thử Evaluation Harness cho RAG.
 */
@Service
public class EvaluationHarnessRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationHarnessRunner.class);

    private final RetrievalService retrievalService;
    private final RagEvaluatorService ragEvaluatorService;
    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public EvaluationHarnessRunner(
            RetrievalService retrievalService,
            RagEvaluatorService ragEvaluatorService,
            DepartmentRepository departmentRepository) {
        this.retrievalService = retrievalService;
        this.ragEvaluatorService = ragEvaluatorService;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Nạp bộ test case từ JSON và thực thi đánh giá toàn bộ.
     */
    public RagEvaluationReportDto runEvaluation() {
        List<EvalTestCase> testCases = loadDataset();
        log.info("Bắt đầu thực thi Evaluation Harness cho {} test cases...", testCases.size());

        List<EvalResultDto> singleResults = new ArrayList<>();

        for (EvalTestCase tc : testCases) {
            try {
                User mockUser = buildMockUser(tc);
                RagChatRequest request = new RagChatRequest(tc.question());
                
                RagChatResponse response = null;
                try {
                    response = retrievalService.search(request, mockUser);
                } catch (Exception ex) {
                    log.warn("Lỗi khi tìm kiếm câu hỏi test case [{}]: {}", tc.id(), ex.getMessage());
                }

                EvalResultDto result = ragEvaluatorService.evaluateSingleCase(tc, response);
                singleResults.add(result);
            } catch (Exception e) {
                log.error("Lỗi thực thi test case [{}]: {}", tc.id(), e.getMessage(), e);
            }
        }

        RagEvaluationReportDto report = ragEvaluatorService.aggregateReport(singleResults);
        log.info("Hoàn tất Evaluation Harness: Tổng {} cases | Leak Rate: {}% | Citation Accuracy: {}% | Avg Relevance: {} | Avg Faithfulness: {}",
                report.totalTestCases(),
                String.format("%.2f", report.crossDeptLeakRate()),
                String.format("%.2f", report.citationAccuracyRate()),
                String.format("%.2f", report.avgContextRelevance()),
                String.format("%.2f", report.avgFaithfulness()));

        return report;
    }

    private List<EvalTestCase> loadDataset() {
        try {
            ClassPathResource resource = new ClassPathResource("eval/dataset_20_questions.json");
            InputStream is = null;
            if (resource.exists()) {
                is = resource.getInputStream();
            } else {
                is = EvaluationHarnessRunner.class.getClassLoader().getResourceAsStream("eval/dataset_20_questions.json");
            }

            if (is == null) {
                throw new IllegalStateException("Resource eval/dataset_20_questions.json không tìm thấy trên Classpath!");
            }

            try (InputStream stream = is) {
                return objectMapper.readValue(stream, new TypeReference<List<EvalTestCase>>() {});
            }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi nạp dataset JSON: " + e.getMessage(), e);
        }
    }

    private User buildMockUser(EvalTestCase tc) {
        String deptCode = tc.userDepartmentCode() != null ? tc.userDepartmentCode() : "GENERAL";
        UUID departmentId = departmentRepository.findByCode(deptCode)
                .map(d -> d.getId())
                .orElseGet(() -> UUID.nameUUIDFromBytes(("DEPT_" + deptCode).getBytes(StandardCharsets.UTF_8)));

        Role role = tc.userRole() != null ? tc.userRole() : Role.ROLE_EMPLOYEE;

        return User.builder()
                .id(UUID.nameUUIDFromBytes(("USER_" + tc.id()).getBytes(StandardCharsets.UTF_8)))
                .username("eval_user_" + tc.id().toLowerCase())
                .email("eval_" + tc.id().toLowerCase() + "@eap.com")
                .role(role)
                .departmentId(departmentId)
                .fullName("Eval User " + tc.id())
                .build();
    }
}
