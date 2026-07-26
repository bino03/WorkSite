package com.management.managementapi.enterprises.dto.enterprise;



import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.management.managementapi.enterprises.model.EnterprisesLocation;
import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;
import com.management.managementapi.enterprises.model.enums.EnterPriseType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseDTO {
    private UUID id;
    private String name;
    private String internalReference;
    private EnterPriseType type;
    private EnterPriseStatus status;
    private LocalDate startDate;
    private LocalDate completionDate;
    private BigDecimal totalArea;
    private BigDecimal landArea;
    private Integer totalUnits;
    private BigDecimal totalInvestment;
    private BigDecimal currentValue;
    private String currency;
    private String constructionCompany;
    private String architect;
    private UUID managerId;
    private UUID ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
        private UUID updatedby;
    private Boolean isActive;
    private EnterprisesLocation location;
    private String banner; // ← ADICIONAR ESTE CAMPO

}