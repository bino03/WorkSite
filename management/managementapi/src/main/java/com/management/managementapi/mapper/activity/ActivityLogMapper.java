package com.management.managementapi.mapper.activity;

import com.management.managementapi.dto.activity.ActivityLogCreateDTO;
import com.management.managementapi.dto.activity.ActivityLogListDTO;
import com.management.managementapi.dto.activity.ActivityLogResponseDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.ActivityLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface ActivityLogMapper {

    @Mapping(target = "id",           ignore = true)
    @Mapping(target = "createdAt",    ignore = true)
    @Mapping(target = "updatedAt",    ignore = true)
    ActivityLog toEntity(ActivityLogCreateDTO dto);

    @Mapping(target = "id",           source = "id")
    @Mapping(target = "userId",       source = "userId")
    @Mapping(target = "userName",     source = "userName")
    @Mapping(target = "activityType", source = "activityType")
    @Mapping(target = "entityType",   source = "entityType")
    @Mapping(target = "entityId",     source = "entityId")
    @Mapping(target = "entityName",   source = "entityName")
    @Mapping(target = "description",  source = "description")
    @Mapping(target = "createdAt",    source = "createdAt")
    ActivityLogResponseDTO toResponse(ActivityLog entity);

    @Mapping(target = "id",           source = "id")
    @Mapping(target = "userName",     source = "userName")
    @Mapping(target = "activityType", source = "activityType")
    @Mapping(target = "entityType",   source = "entityType")
    @Mapping(target = "entityName",   source = "entityName")
    @Mapping(target = "createdAt",    source = "createdAt")
    ActivityLogListDTO toListDTO(ActivityLog entity);
}
