package com.management.managementapi.mapper.common;

import com.management.managementapi.dto.common.location.LocationDetailsResponseDTO;
import com.management.managementapi.dto.common.location.LocationUpdateDTO;
import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.dto.common.location.LocationResponseDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.Location;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface LocationMapper {

    // Entity -> Response
    LocationResponseDTO toResponse(Location entity);

    // DTO -> Entity (create)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", ignore = true)
    Location toEntity(LocationUpsertDTO dto);

    // DTO -> Entity (update no mesmo objeto; nulls são ignorados via config)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", ignore = true)
    void updateEntity(LocationUpsertDTO dto, @MappingTarget Location entity);


    default LocationDetailsResponseDTO toDetailsResponse(Location l) {
        if (l == null) return null;

        var dto = new LocationDetailsResponseDTO();
        dto.setId(l.getId());
        dto.setName(l.getName());
        dto.setAddressLine1(l.getAddressLine1());
        dto.setAddressLine2(l.getAddressLine2());
        dto.setPostalCode(l.getPostalCode());
        dto.setCity(l.getCity());
        dto.setParish(l.getParish());
        dto.setMunicipality(l.getMunicipality());
        dto.setCountry(l.getCountry());

        if (l.getLatitude() != null) {
            if (l.getLatitude() instanceof BigDecimal) {
                dto.setLatitude((BigDecimal) l.getLatitude());
            } else {
                dto.setLatitude(BigDecimal.valueOf(((Number) l.getLatitude()).doubleValue()));
            }
        }
        if (l.getLongitude() != null) {
            if (l.getLongitude() instanceof BigDecimal) {
                dto.setLongitude((BigDecimal) l.getLongitude());
            } else {
                dto.setLongitude(BigDecimal.valueOf(((Number) l.getLongitude()).doubleValue()));
            }
        }

        dto.setGooglePlaceId(l.getGooglePlaceId());
        dto.setNotes(l.getNotes());

        dto.setCreatedAt(toInstant(l.getCreatedAt()));
        dto.setUpdatedAt(toInstant(l.getUpdatedAt()));

        return dto;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
        if (value instanceof Timestamp ts) return ts.toInstant();
        throw new IllegalArgumentException("Unsupported temporal type: " + value.getClass());
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDTO(LocationUpdateDTO dto, @MappingTarget Location entity);

}
