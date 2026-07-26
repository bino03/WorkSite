package com.management.managementapi.dto.common.media;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaResponseDTO {
    private UUID id;
    private String type;
    private String bucket;
    private String storageKey;
    private String mimeType;
    private Long fileSizeBytes;
    private Integer width;
    private Integer height;
    private Integer durationMs;
    private String altText;
    private Integer sortOrder;
    private String downloadUrl;
}
