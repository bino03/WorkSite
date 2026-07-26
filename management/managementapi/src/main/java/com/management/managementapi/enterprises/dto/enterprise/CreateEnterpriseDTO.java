package com.management.managementapi.enterprises.dto.enterprise;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.enterprises.dto.media.EnterpriseMediaDTO;
import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;
import com.management.managementapi.enterprises.model.enums.EnterPriseType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEnterpriseDTO {
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
    private UUID createdBy;
    private UUID updatedby;
    private Boolean isActive;

    // Opção B: Dados para criar nova localização
    private LocationUpsertDTO newLocation;
     private UUID ExistingLocationId;

     // MEDIA
     private List<EnterpriseMediaDTO> media;
}