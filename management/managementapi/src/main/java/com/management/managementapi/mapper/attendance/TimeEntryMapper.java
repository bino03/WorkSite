package com.management.managementapi.mapper.attendance;

import java.time.ZoneId;
import java.util.List;

import org.mapstruct.Mapper;

import com.management.managementapi.dto.attendance.response.TimeEntryResponseDTO;
import com.management.managementapi.dto.attendance.response.TimeEntryRevisionResponseDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.TimeEntryRevision;

/**
 * O fuso entra como parâmetro em vez de ser injetado: mantém o mapper sem estado
 * e testável, e deixa explícito em cada chamada que "o dia" de uma picagem depende
 * de um fuso — a coisa mais fácil de esquecer neste módulo.
 */
@Mapper(config = GlobalMapperConfig.class)
public interface TimeEntryMapper {

    default TimeEntryResponseDTO toResponse(TimeEntry entry, ZoneId zone) {
        if (entry == null) {
            return null;
        }
        return new TimeEntryResponseDTO(
                entry.getId(),
                entry.getProfile().getId(),
                entry.getProfile().getName(),
                entry.getEnterprise() == null ? null : entry.getEnterprise().getId(),
                entry.getEnterprise() == null ? null : entry.getEnterprise().getName(),
                entry.getHappenedAt(),
                entry.getHappenedAt().atZoneSameInstant(zone).toLocalDate(),
                entry.getDirection(),
                entry.getSource(),
                entry.getRegisteredBy() == null ? null : entry.getRegisteredBy().getName(),
                entry.getNote(),
                entry.getDeletedAt());
    }

    default List<TimeEntryResponseDTO> toResponses(List<TimeEntry> entries, ZoneId zone) {
        return entries.stream().map(entry -> toResponse(entry, zone)).toList();
    }

    default TimeEntryRevisionResponseDTO toRevisionResponse(TimeEntryRevision revision) {
        return new TimeEntryRevisionResponseDTO(
                revision.getId(),
                revision.getChange(),
                revision.getChangedByName(),
                revision.getCreatedAt(),
                revision.getReason(),
                revision.getPreviousHappenedAt(),
                revision.getPreviousDirection(),
                revision.getPreviousEnterpriseId(),
                revision.getPreviousNote(),
                revision.getPreviousDeletedAt());
    }
}
