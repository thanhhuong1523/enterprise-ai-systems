package com.vccorp.eap.dto.document;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAliasRequest(
    @NotNull(message = "ID tài liệu gốc không được để trống") UUID originalDocumentId,
    @NotNull(message = "ID phòng ban liên kết không được để trống") UUID aliasDepartmentId
) {}
