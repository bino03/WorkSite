package com.management.managementapi.enterprises.dto.location;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseLocationListDTO {
    private UUID id;
    private UUID locationId;
    private String addressLine1;
    private String addressLine2;
    private String postalCode;
    private String city;
    private String parish;
    private String municipality;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String googlePlaceId;
    private String notes;
    private Boolean isPrimary;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}