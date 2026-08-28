package com.vccorp.eap.service.document;

import java.util.Map;

public interface LlmMetadataExtractorService {
    Map<String, Object> extractMetadata(String query);
    Map<String, Object> extractChunkMetadata(String chunkContent);
    Map<String, Object> extractChunkMetadata(String chunkContent, String headingContext);
}
