package com.management.managementapi.enterprises.mapper;

import com.management.managementapi.enterprises.dto.stage.ConstructionStageResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionStage;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ConstructionStageMapper {

    @Mapping(target = "enterpriseId", source = "enterprise.id")
    ConstructionStageResponseDTO toResponse(ConstructionStage entity);

    List<ConstructionStageResponseDTO> toResponseList(List<ConstructionStage> entities);
}
