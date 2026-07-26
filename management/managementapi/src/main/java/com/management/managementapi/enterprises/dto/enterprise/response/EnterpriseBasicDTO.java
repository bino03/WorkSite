package com.management.managementapi.enterprises.dto.enterprise.response;

import java.util.UUID;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnterpriseBasicDTO {

    private UUID id;
    private String name;
    private String internalReference;
    private String banner;
    private String bannerUrl;
}
