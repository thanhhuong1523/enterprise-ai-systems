package com.vccorp.eap.mcp.resilience;

import java.util.Map;

public interface JsonSelfCorrectionService {
    Map<String, Object> validateAndCorrect(String rawJson);
    String cleanLocal(String raw);
}
