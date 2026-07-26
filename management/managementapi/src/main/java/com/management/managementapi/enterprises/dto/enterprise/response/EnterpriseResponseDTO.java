package com.management.managementapi.enterprises.dto.enterprise.response;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.management.managementapi.enterprises.model.enums.EnterPriseType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseResponseDTO {
    private UUID id;
    private String name;
        private EnterPriseType type;
    private String internalReference;
    private String status;
    private LocalDateTime createdAt;
    private Boolean isActive;
    private LocalDate startDate;
    private LocalDate completionDate;
}