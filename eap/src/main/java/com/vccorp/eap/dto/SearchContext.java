package com.vccorp.eap.dto;

import java.util.Map;
import java.util.UUID;

public record SearchContext(
    String message,
    float[] queryVector,
    Map<String, Object> metadataFilter,
    UUID userDeptId,
    UUID boardDeptId
) {}
