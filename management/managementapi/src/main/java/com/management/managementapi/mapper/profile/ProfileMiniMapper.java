package com.management.managementapi.mapper.profile;

import com.management.managementapi.dto.profile.ProfileMiniDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.Profile;
import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface ProfileMiniMapper {
  @Mapping(source = "id", target = "id")
  @Mapping(source = "name", target = "name")
  ProfileMiniDTO toMini(Profile entity);
}
