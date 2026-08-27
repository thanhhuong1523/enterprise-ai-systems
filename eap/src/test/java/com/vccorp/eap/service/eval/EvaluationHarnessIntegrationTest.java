package com.vccorp.eap.service.eval;

import com.vccorp.eap.dto.ChunkResultDto;
import com.vccorp.eap.dto.RagChatRequest;
import com.vccorp.eap.dto.RagChatResponse;
import com.vccorp.eap.dto.eval.RagEvaluationReportDto;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.RetrievalService;
import com.vccorp.eap.service.eval.impl.RagEvaluatorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class EvaluationHarnessIntegrationTest {

    private EvaluationHarnessRunner evaluationHarnessRunner;

    @BeforeEach
    void setUp() {
        RetrievalService retrievalService = (RetrievalService) Proxy.newProxyInstance(
                RetrievalService.class.getClassLoader(),
                new Class<?>[]{RetrievalService.class},
                (proxy, method, args) -> {
                    if ("search".equals(method.getName())) {
                        RagChatRequest req = (RagChatRequest) args[0];
                        String q = req.message().toLowerCase();

                        // Nếu là câu hỏi truy vấn thông tin bảo mật Ban Giám Đốc (BOARD) từ user không thuộc BOARD -> Trả về không tìm thấy (Block)
                        if (q.contains("bí mật") || q.contains("tái cơ cấu") || q.contains("m&a") || q.contains("thoái vốn") 
                                || q.contains("cổ tức") || q.contains("tín dụng") || q.contains("lương mật") 
                                || q.contains("chưa công bố") || q.contains("cổ đông") || q.contains("hđqt") 
                                || q.contains("ban giám đốc") || q.contains("quy hoạch nhân sự") || q.contains("rủi ro")
                                || q.contains("nội bộ của ban giám đốc") || q.contains("bí mật hđqt") || q.contains("lương bảo mật")) {
                            return new RagChatResponse("Không tìm thấy kết quả phù hợp.", Collections.emptyList());
                        }

                        // Nếu hỏi về tài liệu Ninh Bình (nb)
                        if (q.contains("ninh bình") || q.contains("tràng an") || q.contains("bái đính") || q.contains("hoa lư") 
                                || q.contains("tam cốc") || q.contains("thịt dê") || q.contains("cơm cháy") || q.contains("hạ long trên cạn")) {
                            ChunkResultDto chunk = new ChunkResultDto(
                                    "Ninh Bình là cố đô Đại Cồ Việt, có danh thắng Tràng An, Chùa Bái Đính và đặc sản thịt dê núi cơm cháy.",
                                    0.92,
                                    "nb",
                                    "ORIG_01800094",
                                    Map.of("page_number", 1),
                                    1,
                                    "[nb, Trang 1]"
                            );
                            return new RagChatResponse("Thông tin về Ninh Bình [nb, Trang 1]", List.of(chunk));
                        }

                        // Mặc định trả về câu trả lời cho tài liệu Tết (tet)
                        ChunkResultDto chunk = new ChunkResultDto(
                                "Tết Nguyên Đán là dịp sum vầy gia đình, có bánh chưng, bánh tét, hoa đào, hoa mai và tục mừng tuổi lì xì.",
                                0.95,
                                "tet",
                                "ORIG_01800092",
                                Map.of("page_number", 1),
                                1,
                                "[tet, Trang 1]"
                        );

                        return new RagChatResponse("Thông tin về ngày Tết [tet, Trang 1]", List.of(chunk));
                    }
                    return null;
                }
        );

        DepartmentRepository departmentRepository = (DepartmentRepository) Proxy.newProxyInstance(
                DepartmentRepository.class.getClassLoader(),
                new Class<?>[]{DepartmentRepository.class},
                (proxy, method, args) -> {
                    if ("findByCode".equals(method.getName())) {
                        return Optional.empty();
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );

        RagEvaluatorService ragEvaluatorService = new RagEvaluatorServiceImpl();

        evaluationHarnessRunner = new EvaluationHarnessRunner(
                retrievalService,
                ragEvaluatorService,
                departmentRepository
        );
    }

    @Test
    void testRunEvaluation_ExecutesDatasetSuccessfully() {
        RagEvaluationReportDto report = evaluationHarnessRunner.runEvaluation();

        assertNotNull(report);
        assertEquals(20, report.totalTestCases(), "Phải thực thi đúng 20 test cases");
        assertEquals(0.0, report.crossDeptLeakRate(), 0.001, "Cross-Dept Leak Rate phải bằng 0.0%");
        assertTrue(report.citationAccuracyRate() >= 0.0);
        assertTrue(report.avgContextRelevance() >= 0.0);
        assertTrue(report.avgFaithfulness() >= 0.0);
        assertEquals(20, report.detailsList().size());
    }
}
