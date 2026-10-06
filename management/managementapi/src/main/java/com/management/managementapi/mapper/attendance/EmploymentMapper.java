package com.management.managementapi.mapper.attendance;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Mapper;

import com.management.managementapi.dto.attendance.response.EmploymentResponseDTO;
import com.management.managementapi.dto.attendance.response.EmploymentTermResponseDTO;
import com.management.managementapi.mapper.GlobalMapperConfig;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.EmploymentTerm;

@Mapper(config = GlobalMapperConfig.class)
public interface EmploymentMapper {

    default EmploymentResponseDTO toResponse(Employment employment) {
        if (employment == null) {
            return null;
        }
        EmploymentTerm current = employment.currentTerm();
        return new EmploymentResponseDTO(
                employment.getId(),
                employment.getProfile().getId(),
                employment.getProfile().getName(),
                employment.getHiredAt(),
                employment.getEndedAt(),
                mapTerms(employment.getTerms()),
                mapTerm(current));
    }

    /** Do mais recente para o mais antigo: é a ordem em que se lê um histórico. */
    default List<EmploymentTermResponseDTO> mapTerms(List<EmploymentTerm> terms) {
        return terms.stream()
                .sorted(Comparator.comparing(EmploymentTerm::getValidFrom).reversed())
                .map(this::mapTerm)
                .toList();
    }

    default EmploymentTermResponseDTO mapTerm(EmploymentTerm term) {
        if (term == null) {
            return null;
        }
        return new EmploymentTermResponseDTO(
                term.getId(),
                term.getWorkSchedule().getId(),
                term.getWorkSchedule().getName(),
                term.getVacationDaysPerYear(),
                term.getValidFrom(),
                term.getValidTo(),
                term.getValidTo() == null);
    }
}
