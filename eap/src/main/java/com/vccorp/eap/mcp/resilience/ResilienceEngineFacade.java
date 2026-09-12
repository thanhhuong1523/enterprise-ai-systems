package com.vccorp.eap.mcp.resilience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.mcp.resilience.JsonSelfCorrectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tầng bọc tích hợp 4 cấp phục hồi dữ liệu JSON (Resilience Engine Facade).
 */
@Component
public class ResilienceEngineFacade {

    private static final Logger log = LoggerFactory.getLogger(ResilienceEngineFacade.class);

    private final LocalRegexSanitizer localRegexSanitizer;
    private final StackBracketBalancer stackBracketBalancer;
    private final JsonSelfCorrectionService jsonSelfCorrectionService;
    private final ObjectMapper objectMapper;

    public ResilienceEngineFacade(LocalRegexSanitizer localRegexSanitizer,
                                  StackBracketBalancer stackBracketBalancer,
                                  JsonSelfCorrectionService jsonSelfCorrectionService,
                                  ObjectMapper objectMapper) {
        this.localRegexSanitizer = localRegexSanitizer;
        this.stackBracketBalancer = stackBracketBalancer;
        this.jsonSelfCorrectionService = jsonSelfCorrectionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Phân tích và tự phục hồi chuỗi JSON qua 4 tầng.
     */
    public Map<String, Object> parseAndRecover(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Chuỗi JSON từ LLM rỗng.");
        }

        // Tầng 1: Regex làm sạch nhanh
        String tier1 = localRegexSanitizer.sanitize(rawJson);
        try {
            return objectMapper.readValue(tier1, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e1) {
            log.debug("Tầng 1 (Regex) chưa phân tích được JSON: {}. Chuyển sang Tầng 2...", e1.getMessage());
        }

        // Tầng 2: Cân bằng Stack & Khép chuỗi
        String tier2 = stackBracketBalancer.balance(tier1);
        try {
            return objectMapper.readValue(tier2, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e2) {
            log.warn("Tầng 2 (Stack Balancer) chưa phân tích được JSON: {}. Kích hoạt Tầng 3 (LLM Re-prompt)...", e2.getMessage());
        }

        // Tầng 3: Re-prompt LLM qua JsonSelfCorrectionService
        try {
            Map<String, Object> tier3Result = jsonSelfCorrectionService.validateAndCorrect(tier2);
            if (tier3Result != null && !tier3Result.isEmpty()) {
                return tier3Result;
            }
        } catch (Exception e3) {
            log.error("Tầng 3 (LLM Re-prompt) thất bại: {}. Kích hoạt Tầng 4 (Circuit Breaker)...", e3.getMessage());
        }

        // Tầng 4: Circuit Breaker ngắt an toàn, không làm sập JVM
        throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST,
                "Không thể khôi phục định dạng JSON từ mô hình AI sau 4 tầng tự phục hồi.");
    }
}
