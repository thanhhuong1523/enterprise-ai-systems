package com.vccorp.eap.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAliasRequest {
    @NotNull(message = "ID tài liệu gốc không được để trống")
    private UUID originalDocumentId;

    @NotNull(message = "ID phòng ban liên kết không được để trống")
    private UUID aliasDepartmentId;
}
