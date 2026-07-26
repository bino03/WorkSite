package com.management.managementapi.enterprises.mapper;

import com.management.managementapi.enterprises.dto.enterprise.CreateEnterpriseDTO;
import com.management.managementapi.enterprises.dto.enterprise.EnterpriseDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseBasicDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseFullResponseDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseListDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseResponseDTO;
import com.management.managementapi.enterprises.dto.location.EnterpriseLocationListDTO;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.EnterprisesLocation;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EnterpriseMapper {

    // Entity to DTO
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "updatedby", ignore = true)
    EnterpriseDTO toDTO(Enterprise enterprise);

    @Mapping(target = "createdbyName", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "media", ignore = true)
    EnterpriseFullResponseDTO toDTOFull(Enterprise enterprise);

    EnterpriseResponseDTO toResponseDTO(Enterprise enterprise);

    List<EnterpriseDTO> toDTOList(List<Enterprise> enterprises);

    List<EnterpriseResponseDTO> toResponseDTOList(List<Enterprise> enterprises);

    // DTO to Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "banner", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "status", qualifiedByName = "mapStatus")
    Enterprise toEntity(CreateEnterpriseDTO createEnterpriseDTO);

    @org.mapstruct.Named("mapStatus")
    default com.management.managementapi.enterprises.model.enums.EnterPriseStatus mapStatus(
            com.management.managementapi.enterprises.model.enums.EnterPriseStatus status) {
        return status != null ? status : com.management.managementapi.enterprises.model.enums.EnterPriseStatus.planning;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDTO(EnterpriseDTO enterpriseDTO, @MappingTarget Enterprise enterprise);

    Enterprise toEntity(EnterpriseDTO enterpriseDTO);

    // List DTOs
    @Mapping(target = "location", ignore = true)
    EnterpriseListDTO toListDTO(Enterprise enterprise);

    List<EnterpriseListDTO> toListDTOList(List<Enterprise> enterprises);

    // Location mapping
    default EnterpriseLocationListDTO toEnterpriseLocationListDTO(EnterprisesLocation el) {
        if (el == null) {
            return null;
        }

        EnterpriseLocationListDTO dto = new EnterpriseLocationListDTO();
        dto.setId(el.getId());
        dto.setIsPrimary(el.getIsPrimary());
        dto.setSortOrder(el.getSortOrder());
        dto.setCreatedAt(el.getCreatedAt());
        dto.setUpdatedAt(el.getUpdatedAt());

        if (el.getLocation() != null) {
            dto.setLocationId(el.getLocation().getId());
            dto.setAddressLine1(el.getLocation().getAddressLine1());
            dto.setAddressLine2(el.getLocation().getAddressLine2());
            dto.setPostalCode(el.getLocation().getPostalCode());
            dto.setCity(el.getLocation().getCity());
            dto.setParish(el.getLocation().getParish());
            dto.setMunicipality(el.getLocation().getMunicipality());
            dto.setCountry(el.getLocation().getCountry());
            dto.setLatitude(el.getLocation().getLatitude());
            dto.setLongitude(el.getLocation().getLongitude());
            dto.setGooglePlaceId(el.getLocation().getGooglePlaceId());
            dto.setNotes(el.getLocation().getNotes());
        }

        return dto;
    }

    // Alias para compatibilidade
    default EnterpriseLocationListDTO toLocationDTO(EnterprisesLocation el) {
        return toEnterpriseLocationListDTO(el);
    }

    // Basic DTO (para dropdowns)
    @Mapping(target = "bannerUrl", ignore = true)
    EnterpriseBasicDTO toBasicDTO(Enterprise enterprise);

    List<EnterpriseBasicDTO> toBasicDTOList(List<Enterprise> enterprises);
}
