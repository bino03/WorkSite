package com.management.managementapi.enterprises.dto.enterprise;

import java.time.LocalDateTime;
import java.util.UUID;

import com.management.managementapi.model.Location;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseLocationDTO {

    private UUID id; // ID da EnterprisesLocation

    private UUID enterpriseId; // ID da Enterprise
    
    private Location location; // Objeto completo da Location
    
    private Boolean isPrimary;
    
    private Integer sortOrder;
    
    private String notes;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}