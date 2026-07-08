package com.vccorp.eap.integration;

import com.vccorp.eap.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @Test
    void requestWithInvalidToken_Returns401() throws Exception {
        String token = "invalid-token";
        when(jwtService.validateToken(token)).thenReturn(false);

        mockMvc.perform(get("/api/v1/original-documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"));
    }

    @Test
    void requestWithExpiredToken_Returns401() throws Exception {
        String token = "expired-token";
        when(jwtService.validateToken(token)).thenReturn(false);

        mockMvc.perform(get("/api/v1/original-documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"));
    }

    @Test
    void requestWithForgedToken_Returns401() throws Exception {
        String token = "forged-token";
        when(jwtService.validateToken(token)).thenReturn(false);

        mockMvc.perform(get("/api/v1/original-documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"));
    }
}
