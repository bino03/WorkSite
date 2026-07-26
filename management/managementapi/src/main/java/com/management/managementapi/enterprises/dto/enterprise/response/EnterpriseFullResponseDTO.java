package com.management.managementapi.enterprises.dto.enterprise.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.management.managementapi.dto.common.location.LocationResponseDTO;
import com.management.managementapi.dto.common.media.MediaResponseDTO;
import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;
import com.management.managementapi.enterprises.model.enums.EnterPriseType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseFullResponseDTO {

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
    private String createdbyName;
    private Boolean isActive;
    private String banner;

    private LocationResponseDTO location;
    private List<MediaResponseDTO> media;
}
