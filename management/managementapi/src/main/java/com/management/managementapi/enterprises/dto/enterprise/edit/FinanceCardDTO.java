package com.management.managementapi.enterprises.dto.enterprise.edit;

import java.math.BigDecimal;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCardDTO {
 
        private BigDecimal totalInvestment;
        private BigDecimal currentValue;
        private String currency ;
        private String constructionCompany;
        private String architect;    
}
