package com.vccorp.eap.service.helper.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.service.helper.LlmClient;

@Service
public class LlmClientImpl implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClientImpl.class);
    private final String apiKey;
    private final String modelName;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public LlmClientImpl(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-3.5-flash}") String modelName,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta/openai/chat/completions}") String baseUrl,
            @Value("${eap.llm.connect-timeout-ms:1000}") long connectTimeoutMs) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public Map<String, Object> callLlm(String content, String systemPrompt, long timeoutMs) {
        return executeWithTimeout(() -> callLlmApi(content, systemPrompt, timeoutMs), timeoutMs);
    }

    @Override
    public String callLlmText(String content, String systemPrompt, long timeoutMs) {
        Future<String> future = executorService.submit(() -> callLlmTextApi(content, systemPrompt, timeoutMs));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("LLM text generation timed out after {}ms.", timeoutMs);
            future.cancel(true);
            return null;
        } catch (Exception e) {
            log.warn("LLM text generation failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> executeWithTimeout(Callable<Map<String, Object>> callable, long timeoutMs) {
        Future<Map<String, Object>> future = executorService.submit(callable);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("LLM extraction timed out after {}ms. Falling back to empty metadata.", timeoutMs);
            future.cancel(true);
            return Collections.emptyMap();
        } catch (Exception e) {
            log.warn("LLM extraction failed. Falling back to empty metadata: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> callLlmApi(String content, String systemPrompt, long timeoutMs) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API key rỗng, bỏ qua trích xuất metadata qua LLM");
            return Collections.emptyMap();
        }
        
        Map<String, Object> requestBody = Map.of(
            "model", modelName,
            "temperature", 0.0,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", content)
            )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "http://localhost:8080")
                .header("X-Title", "EAP RAG")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        int maxAttempts = 8;
        long delayMs = 3000;
        long maxDelayMs = 15000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
                List<?> choices = (List<?>) responseMap.get("choices");
                if (choices == null || choices.isEmpty()) return Collections.emptyMap();
                
                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String rawContent = (String) message.get("content");
                if (rawContent == null) return Collections.emptyMap();
                
                return filterOmittedKeys(objectMapper.readValue(cleanJsonResponse(rawContent), Map.class));
            } else if (response.statusCode() == 429) {
                log.warn("Gemini API returned 429 (Rate Limit) on attempt {}/{}. Retrying in {}ms...", 
                        attempt, maxAttempts, delayMs);
                if (attempt < maxAttempts) {
                    Thread.sleep(delayMs);
                    delayMs = Math.min(delayMs * 2, maxDelayMs); // Exponential backoff with cap
                }
            } else {
                log.warn("Gemini API returned error code {}: {}", response.statusCode(), response.body());
                break; // Do not retry for other types of errors (e.g. 401, 400)
            }
        }
        return Collections.emptyMap();
    }

    private String callLlmTextApi(String content, String systemPrompt, long timeoutMs) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API key rỗng, bỏ qua sinh văn bản qua LLM");
            return null;
        }

        Map<String, Object> requestBody = Map.of(
            "model", modelName,
            "temperature", 0.2,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", content)
            )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "http://localhost:8080")
                .header("X-Title", "EAP RAG")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        int maxAttempts = 8;
        long delayMs = 3000;
        long maxDelayMs = 15000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);
                List<?> choices = (List<?>) responseMap.get("choices");
                if (choices == null || choices.isEmpty()) return null;

                Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String rawContent = (String) message.get("content");
                return rawContent != null ? rawContent.trim() : null;
            } else if (response.statusCode() == 429) {
                log.warn("Gemini API returned 429 (Rate Limit) on attempt {}/{}. Retrying in {}ms...",
                        attempt, maxAttempts, delayMs);
                if (attempt < maxAttempts) {
                    Thread.sleep(delayMs);
                    delayMs = Math.min(delayMs * 2, maxDelayMs);
                }
            } else {
                log.warn("Gemini API returned error code {}: {}", response.statusCode(), response.body());
                break;
            }
        }
        return null;
    }

    private Map<String, Object> filterOmittedKeys(Map<?, ?> rawJsonMap) {
        if (rawJsonMap == null) return Collections.emptyMap();
        Map<String, Object> cleanMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawJsonMap.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key instanceof String && value != null) {
                if (value instanceof String) {
                    String strVal = ((String) value).trim();
                    if (!strVal.isEmpty()) {
                        cleanMap.put((String) key, strVal);
                    }
                } else if (value instanceof Collection) {
                    Collection<?> coll = (Collection<?>) value;
                    List<Object> cleanList = new ArrayList<>();
                    for (Object item : coll) {
                        if (item != null) {
                            if (item instanceof String) {
                                String s = ((String) item).trim();
                                if (!s.isEmpty()) {
                                    cleanList.add(s);
                                }
                            } else {
                                cleanList.add(item);
                            }
                        }
                    }
                    if (!cleanList.isEmpty()) {
                        cleanMap.put((String) key, cleanList);
                    }
                } else {
                    cleanMap.put((String) key, value);
                }
            }
        }
        return cleanMap;
    }

    private String cleanJsonResponse(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down LlmClientImpl executor service...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
