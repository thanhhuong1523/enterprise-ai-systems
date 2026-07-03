package com.vccorp.eap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.dto.DocumentResponse;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.infrastructure.security.JwtAuthenticationFilter;
import com.vccorp.eap.service.JwtService;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.model.User;
import com.vccorp.eap.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .username("employee")
                .role(Role.ROLE_EMPLOYEE)
                .departmentId(UUID.randomUUID())
                .build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                sampleUser, null, Collections.singletonList(new SimpleGrantedAuthority(sampleUser.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void createAlias_Success() throws Exception {
        UUID origId = UUID.randomUUID();
        UUID targetDeptId = UUID.randomUUID();
        CreateAliasRequest request = new CreateAliasRequest(origId, targetDeptId);

        DocumentResponse aliasDoc = DocumentResponse.builder()
                .id(UUID.randomUUID())
                .businessCode("ALIA_123456")
                .title("Mock Alias")
                .ownerDepartmentId(targetDeptId)
                .build();

        when(documentService.createAlias(any(CreateAliasRequest.class), any(User.class))).thenReturn(aliasDoc);

        mockMvc.perform(post("/api/v1/alias-documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.businessCode").value("ALIA_123456"));
    }

    @Test
    void resolveAlias_Success() throws Exception {
        UUID aliasId = UUID.randomUUID();
        byte[] mockBytes = "mock file content".getBytes();

        when(documentService.resolveAlias(eq(aliasId), any(User.class))).thenReturn(mockBytes);

        mockMvc.perform(get("/api/v1/alias-documents/{id}", aliasId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(mockBytes));
    }
}
