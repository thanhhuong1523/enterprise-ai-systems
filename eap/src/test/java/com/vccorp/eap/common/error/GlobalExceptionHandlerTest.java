package com.vccorp.eap.common.error;

import com.vccorp.eap.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleNoResourceFoundException trả về HTTP 404 và mã lỗi ERR_RESOURCE_NOT_FOUND")
    void handleNoResourceFoundException_Returns404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.POST, "api/v1/mcp/sse");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNoResourceFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().error());
        assertEquals("ERR_RESOURCE_NOT_FOUND", response.getBody().error().errorCode());
        assertTrue(response.getBody().error().message().contains("api/v1/mcp/sse"));
    }

    @Test
    @DisplayName("handleMethodNotSupportedException trả về HTTP 405 và mã lỗi ERR_METHOD_NOT_ALLOWED")
    void handleMethodNotSupportedException_Returns405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST", List.of("GET"));

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleMethodNotSupportedException(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().error());
        assertEquals("ERR_METHOD_NOT_ALLOWED", response.getBody().error().errorCode());
        assertTrue(response.getBody().error().message().contains("POST"));
    }
}
