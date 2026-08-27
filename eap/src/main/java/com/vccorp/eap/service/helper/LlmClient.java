package com.vccorp.eap.service.helper;

import java.util.Map;

public interface LlmClient {
    Map<String, Object> callLlm(String content, String systemPrompt, long timeoutMs);
    String callLlmText(String content, String systemPrompt, long timeoutMs);
}
