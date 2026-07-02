package com.vccorp.eap.dto;

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
    private UUID originalDocumentId;
    private UUID aliasDepartmentId;
}
