package com.management.managementapi.enterprises.mapper;

import com.management.managementapi.dto.common.media.MediaResponseDTO;
import com.management.managementapi.enterprises.model.EnterprisesMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EnterprisesMediaMapper {

    /**
     * Converte EnterprisesMedia entity para MediaResponseDTO
     */
    @Mapping(target = "type", expression = "java(entity.getType() != null ? entity.getType().name() : null)")
    @Mapping(target = "bucket", source = "bucket")
    @Mapping(target = "storageKey", source = "storageKey")
    @Mapping(target = "mimeType", source = "mimeType")
    @Mapping(target = "fileSizeBytes", source = "fileSizeBytes")
    @Mapping(target = "width", source = "width")
    @Mapping(target = "height", source = "height")
    @Mapping(target = "durationMs", source = "durationMs")
    @Mapping(target = "altText", source = "altText")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "downloadUrl", ignore = true) // Será preenchido no Service com signed URL
    MediaResponseDTO toResponse(EnterprisesMedia entity);



}