package com.management.managementapi.enterprises.dto.enterprise.edit;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatesAndAreasCardDTO {
    
    private BigDecimal totalArea;

        private BigDecimal landArea;
    private LocalDate completionDate;
    private LocalDate startDate;

}
