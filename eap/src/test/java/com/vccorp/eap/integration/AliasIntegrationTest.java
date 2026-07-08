package com.vccorp.eap.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.dto.CreateAliasRequest;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.Department;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AliasIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DepartmentRepository departmentRepository;

    @MockBean
    private JwtService jwtService;

    private UUID deptId;
    private UUID boardDeptId;
    private UUID originalDocId;
    private String employeeToken;
    private String boardToken;

    @BeforeEach
    void setUp() {
        deptId = UUID.randomUUID();
        boardDeptId = UUID.randomUUID();
        originalDocId = new UUID(99999L, 2L); // Ensure even LSB (original)

        employeeToken = "valid-employee-token";
        boardToken = "valid-board-token";

        // Setup mock user for Employee (ROLE_DEPT_MANAGER/ROLE_EMPLOYEE)
        Claims empClaims = Mockito.mock(Claims.class);
        when(empClaims.get("id", String.class)).thenReturn(UUID.randomUUID().toString());
        when(empClaims.getSubject()).thenReturn("dept_manager");
        when(empClaims.get("role", String.class)).thenReturn(Role.ROLE_DEPT_MANAGER.name());
        when(empClaims.get("email", String.class)).thenReturn("manager@vccorp.vn");
        when(empClaims.get("departmentId", String.class)).thenReturn(deptId.toString());

        // Setup mock user for Board (ROLE_BOARD)
        Claims boardClaims = Mockito.mock(Claims.class);
        when(boardClaims.get("id", String.class)).thenReturn(UUID.randomUUID().toString());
        when(boardClaims.getSubject()).thenReturn("board_member");
        when(boardClaims.get("role", String.class)).thenReturn(Role.ROLE_BOARD.name());
        when(boardClaims.get("email", String.class)).thenReturn("board@vccorp.vn");
        when(boardClaims.get("departmentId", String.class)).thenReturn(boardDeptId.toString());

        // Stub token validation
        when(jwtService.validateToken(employeeToken)).thenReturn(true);
        when(jwtService.parseToken(employeeToken)).thenReturn(empClaims);

        when(jwtService.validateToken(boardToken)).thenReturn(true);
        when(jwtService.parseToken(boardToken)).thenReturn(boardClaims);

        // Stub department names
        when(departmentRepository.findById(boardDeptId))
                .thenReturn(Optional.of(new Department(boardDeptId, "BOARD", "Ban Giám Đốc")));
        when(departmentRepository.findById(deptId))
                .thenReturn(Optional.of(new Department(deptId, "DEV", "Development")));
    }

    @Test
    void createAlias_BOARDReceiveAlias_ThrowsBoardProtection() throws Exception {
        // Original doc belongs to DEV department
        Document originalDoc = Document.builder()
                .id(originalDocId)
                .ownerDepartmentId(deptId)
                .title("Original Report")
                .parentId(null)
                .build();

        when(documentRepository.findByIdForUpdate(originalDocId)).thenReturn(Optional.of(originalDoc));
        when(departmentRepository.existsById(boardDeptId)).thenReturn(true);

        CreateAliasRequest request = new CreateAliasRequest(originalDocId, boardDeptId);

        mockMvc.perform(post("/api/v1/alias-documents")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_BOARD_PROTECTION"))
                .andExpect(jsonPath("$.message").value("Không thể chia sẻ tài liệu đến phòng Ban Giám Đốc."));
    }

    @Test
    void createAlias_BOARDSendAlias_ThrowsForbiddenRole() throws Exception {
        // Original doc belongs to BOARD department
        Document originalDoc = Document.builder()
                .id(originalDocId)
                .ownerDepartmentId(boardDeptId)
                .title("Board Secret Document")
                .parentId(null)
                .build();

        when(documentRepository.findByIdForUpdate(originalDocId)).thenReturn(Optional.of(originalDoc));
        when(departmentRepository.existsById(deptId)).thenReturn(true);

        CreateAliasRequest request = new CreateAliasRequest(originalDocId, deptId);

        mockMvc.perform(post("/api/v1/alias-documents")
                        .header("Authorization", "Bearer " + boardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ERR_FORBIDDEN_ROLE"));
    }

    @Test
    void createAlias_NormalSuccess() throws Exception {
        UUID targetDeptId = UUID.randomUUID();
        when(departmentRepository.findById(targetDeptId))
                .thenReturn(Optional.of(new Department(targetDeptId, "HR", "Human Resources")));
        when(departmentRepository.existsById(targetDeptId)).thenReturn(true);

        // Original doc belongs to DEV department
        Document originalDoc = Document.builder()
                .id(originalDocId)
                .ownerDepartmentId(deptId)
                .title("Original Report")
                .parentId(null)
                .build();

        when(documentRepository.findByIdForUpdate(originalDocId)).thenReturn(Optional.of(originalDoc));
        when(documentRepository.existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(originalDocId, targetDeptId))
                .thenReturn(false);

        Document savedAlias = Document.builder()
                .id(UUID.randomUUID())
                .businessCode("ALIA_MOCK12")
                .title("Original Report")
                .ownerDepartmentId(targetDeptId)
                .parentId(originalDocId)
                .build();
        when(documentRepository.save(any(Document.class))).thenReturn(savedAlias);

        CreateAliasRequest request = new CreateAliasRequest(originalDocId, targetDeptId);

        mockMvc.perform(post("/api/v1/alias-documents")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
