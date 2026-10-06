package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.attendance.request.HolidayUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.model.attendance.Holiday;
import com.management.managementapi.model.enums.HolidayScope;
import com.management.managementapi.repository.attendance.HolidayRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O concelho é o que distingue um feriado municipal de um nacional, e a regra tem de
 * valer nos dois sentidos: um municipal sem concelho não se sabe onde se aplica, e um
 * nacional com concelho é contraditório.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HolidayServiceTest {

    @Mock private HolidayRepository repository;

    private HolidayService service() {
        when(repository.save(any(Holiday.class))).thenAnswer(call -> call.getArgument(0));
        return new HolidayService(repository);
    }

    private static HolidayUpsertDTO dto(HolidayScope scope, String municipality) {
        return new HolidayUpsertDTO(LocalDate.of(2026, 6, 10), "Dia de Portugal", scope, municipality);
    }

    @Test
    @DisplayName("um feriado nacional grava-se sem concelho")
    void nacionalSemConcelho() {
        Holiday holiday = service().create(dto(HolidayScope.NATIONAL, null));

        assertThat(holiday.getScope()).isEqualTo(HolidayScope.NATIONAL);
        assertThat(holiday.getMunicipality()).isNull();
        assertThat(holiday.getName()).isEqualTo("Dia de Portugal");
    }

    @Test
    @DisplayName("um feriado municipal exige o concelho")
    void municipalExigeConcelho() {
        assertThatThrownBy(() -> service().create(dto(HolidayScope.MUNICIPAL, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HOLIDAY_MUNICIPALITY_REQUIRED);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("um concelho só com espaços conta como vazio")
    void concelhoEmBrancoContaComoVazio() {
        assertThatThrownBy(() -> service().create(dto(HolidayScope.MUNICIPAL, "   ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HOLIDAY_MUNICIPALITY_REQUIRED);
    }

    @Test
    @DisplayName("um feriado nacional com concelho é recusado — seria contraditório")
    void nacionalNaoLevaConcelho() {
        assertThatThrownBy(() -> service().create(dto(HolidayScope.NATIONAL, "Vila Real")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HOLIDAY_MUNICIPALITY_NOT_ALLOWED);
    }

    @Test
    @DisplayName("um feriado municipal grava o concelho sem espaços em volta")
    void municipalGravaConcelho() {
        Holiday holiday = service().create(dto(HolidayScope.MUNICIPAL, "  Vila Real  "));

        assertThat(holiday.getMunicipality()).isEqualTo("Vila Real");
    }

    @Test
    @DisplayName("mudar um municipal para nacional limpa o concelho")
    void mudarParaNacionalLimpaOConcelho() {
        UUID id = UUID.randomUUID();
        Holiday existente = new Holiday();
        existente.setScope(HolidayScope.MUNICIPAL);
        existente.setMunicipality("Vila Real");
        when(repository.findById(id)).thenReturn(java.util.Optional.of(existente));

        Holiday atualizado = service().update(id, dto(HolidayScope.NATIONAL, null));

        assertThat(atualizado.getScope()).isEqualTo(HolidayScope.NATIONAL);
        assertThat(atualizado.getMunicipality()).isNull();
    }
}
