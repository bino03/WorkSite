package com.management.managementapi.enterprises.mapper;

import com.management.managementapi.enterprises.dto.substage.ConstructionSubStageResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionSubStage;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ConstructionSubStageMapper {

    @Mapping(target = "stageId", source = "stage.id")
    ConstructionSubStageResponseDTO toResponse(ConstructionSubStage entity);

    List<ConstructionSubStageResponseDTO> toResponseList(List<ConstructionSubStage> entities);
}
