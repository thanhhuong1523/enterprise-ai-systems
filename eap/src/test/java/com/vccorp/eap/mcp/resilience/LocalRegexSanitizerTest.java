package com.vccorp.eap.mcp.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalRegexSanitizerTest {

    private LocalRegexSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new LocalRegexSanitizer();
    }

    @Test
    void testSanitize_RemovesMarkdownJsonWrapper() {
        String raw = "```json\n{\"action\": \"listDepartments\"}\n```";
        String cleaned = sanitizer.sanitize(raw);
        assertEquals("{\"action\": \"listDepartments\"}", cleaned);
    }

    @Test
    void testSanitize_RemovesGenericMarkdownWrapper() {
        String raw = "```\n{\"action\": \"listDepartments\"}\n```";
        String cleaned = sanitizer.sanitize(raw);
        assertEquals("{\"action\": \"listDepartments\"}", cleaned);
    }

    @Test
    void testSanitize_RemovesTrailingCommasInObjectsAndArrays() {
        String raw = "{\"name\": \"HR\", \"items\": [1, 2, ], }";
        String cleaned = sanitizer.sanitize(raw);
        assertEquals("{\"name\": \"HR\", \"items\": [1, 2]}", cleaned);
    }

    @Test
    void testSanitize_NullAndEmptyHandling() {
        assertEquals("", sanitizer.sanitize(null));
        assertEquals("", sanitizer.sanitize("   "));
    }
}
