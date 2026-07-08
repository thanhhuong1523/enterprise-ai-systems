package com.vccorp.eap.integration;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.service.AuthService;
import com.vccorp.eap.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RefreshTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    void refresh_CookieMissing_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Refresh Token không được rỗng."));
    }

    @Test
    void refresh_CookieExpiredOrInvalid_ReturnsUnauthenticated() throws Exception {
        Cookie expiredCookie = new Cookie("refreshToken", "expired-token");

        when(authService.refresh(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.ERR_UNAUTHENTICATED, "Refresh Token không tồn tại, đã hết hạn hoặc đã bị thu hồi."));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(expiredCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHENTICATED"));
    }

    @Test
    void logout_RemovesCookie() throws Exception {
        Cookie activeCookie = new Cookie("refreshToken", "active-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(activeCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
