package com.management.managementapi.enterprises.dto.media;

import java.io.InputStream;
import java.util.UUID;

import com.management.managementapi.model.enums.MediaType;
import com.management.managementapi.model.enums.Visibility;

import lombok.Data;


@Data
public class EnterpriseMediaDTO {
    private MediaType type;
    private String storageKey; // Opcional - se não for fornecido, geramos
    private String altText;
    private Integer sortOrder = 0;
    private String mimeType;
    private Long fileSizeBytes;
    private Integer width;
    private Integer height;
    private Integer durationMs;
    private String checksumSha256;
    private Visibility visibility = Visibility.PRIVATE;
    private UUID uploadedBy;
        private InputStream fileContent;

    
}