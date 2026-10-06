package com.management.managementapi.service.attendance;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.attendance.request.WorkScheduleDayDTO;
import com.management.managementapi.dto.attendance.request.WorkScheduleUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.mapper.attendance.WorkScheduleMapper;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentTermRepository;
import com.management.managementapi.repository.attendance.WorkScheduleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * As regras de um horário existem em dois sítios: como CHECK na V42 e aqui no
 * service. A duplicação é deliberada — a base de dados é a última linha de
 * defesa, mas só sabe devolver uma violação de integridade opaca; é o service
 * que diz *qual* das regras falhou, com um SCHED_xxx que o cliente mostra.
 * Estes testes são sobre essa camada, a única que produz a mensagem certa.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkScheduleServiceTest {

    @Mock private WorkScheduleRepository repository;
    @Mock private WorkScheduleMapper mapper;
    @Mock private ProfileRepository profileRepository;
    @Mock private EmploymentTermRepository employmentTermRepository;

    private WorkScheduleService service() {
        return new WorkScheduleService(repository, mapper, profileRepository, employmentTermRepository);
    }

    private static WorkScheduleDayDTO day(DayOfWeek weekday, String start, String end, int breakMinutes) {
        return new WorkScheduleDayDTO(weekday, LocalTime.parse(start), LocalTime.parse(end), breakMinutes);
    }

    private static WorkScheduleUpsertDTO dto(WorkScheduleDayDTO... days) {
        return new WorkScheduleUpsertDTO("08–17 c/ 1h almoço", null, List.of(days));
    }

    @Test
    @DisplayName("um horário válido é gravado, com o nome sem espaços em volta")
    void gravaHorarioValido() {
        when(repository.existsByNameIgnoreCaseAndDeletedAtIsNull(anyString())).thenReturn(false);
        when(repository.save(any(WorkSchedule.class))).thenAnswer(call -> call.getArgument(0));

        WorkScheduleUpsertDTO dto = new WorkScheduleUpsertDTO(
                "  08–17 c/ 1h almoço  ", null,
                List.of(day(DayOfWeek.MONDAY, "08:00", "17:00", 60),
                        day(DayOfWeek.FRIDAY, "08:00", "17:00", 60)));

        WorkSchedule saved = service().create(dto, null);

        assertThat(saved.getName()).isEqualTo("08–17 c/ 1h almoço");
        assertThat(saved.getDays()).hasSize(2);
        assertThat(saved.getDays().getFirst().getSchedule()).isSameAs(saved);
    }

    @Test
    @DisplayName("um dia que não vem na lista não é dia de trabalho")
    void diasAusentesNaoSaoDiasDeTrabalho() {
        when(repository.save(any(WorkSchedule.class))).thenAnswer(call -> call.getArgument(0));

        WorkSchedule saved = service().create(dto(day(DayOfWeek.MONDAY, "08:00", "17:00", 60)), null);

        assertThat(saved.getDays()).hasSize(1);
        assertThat(saved.getDays().getFirst().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("o mesmo dia da semana duas vezes é recusado")
    void recusaDiaRepetido() {
        assertThatThrownBy(() -> service().create(
                dto(day(DayOfWeek.MONDAY, "08:00", "13:00", 0),
                    day(DayOfWeek.MONDAY, "14:00", "18:00", 0)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_DUPLICATE_WEEKDAY);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("saída antes da entrada é recusada — um dia de trabalho não atravessa a meia-noite")
    void recusaSaidaAntesDaEntrada() {
        assertThatThrownBy(() -> service().create(dto(day(DayOfWeek.MONDAY, "22:00", "06:00", 0)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_INVALID_HOURS);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("saída igual à entrada é recusada")
    void recusaDiaDeDuracaoZero() {
        assertThatThrownBy(() -> service().create(dto(day(DayOfWeek.MONDAY, "08:00", "08:00", 0)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_INVALID_HOURS);
    }

    @Test
    @DisplayName("uma pausa que engole o dia todo é recusada")
    void recusaPausaMaiorQueODia() {
        assertThatThrownBy(() -> service().create(dto(day(DayOfWeek.MONDAY, "08:00", "12:00", 240)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_BREAK_TOO_LONG);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("nome repetido entre horários vivos é recusado")
    void recusaNomeRepetido() {
        when(repository.existsByNameIgnoreCaseAndDeletedAtIsNull(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service().create(dto(day(DayOfWeek.MONDAY, "08:00", "17:00", 60)), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_DUPLICATE_NAME);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("as horas previstas do dia descontam a pausa declarada no horário")
    void horasPrevistasDescontamAPausa() {
        when(repository.save(any(WorkSchedule.class))).thenAnswer(call -> call.getArgument(0));

        WorkSchedule saved = service().create(dto(day(DayOfWeek.MONDAY, "08:00", "17:00", 60)), null);

        assertThat(saved.getDays().getFirst().expectedWork().toMinutes()).isEqualTo(480);
    }

    @Test
    @DisplayName("gravar substitui os dias em vez de os acumular")
    void atualizarSubstituiOsDias() {
        UUID id = UUID.randomUUID();
        WorkSchedule existing = new WorkSchedule();
        existing.setName("antigo");
        when(repository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(WorkSchedule.class))).thenAnswer(call -> call.getArgument(0));

        service().update(id, dto(day(DayOfWeek.MONDAY, "08:00", "17:00", 60),
                                 day(DayOfWeek.TUESDAY, "08:00", "17:00", 60)));
        WorkSchedule after = service().update(id, dto(day(DayOfWeek.MONDAY, "09:00", "18:00", 30)));

        assertThat(after.getDays()).hasSize(1);
        assertThat(after.getDays().getFirst().getStartTime()).isEqualTo(LocalTime.parse("09:00"));
    }

    @Test
    @DisplayName("um horário já atribuído a alguém não se edita — cria-se outro")
    void recusaEditarHorarioAtribuido() {
        UUID id = UUID.randomUUID();
        WorkSchedule atribuido = new WorkSchedule();
        atribuido.setName("08–17");
        when(repository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(atribuido));
        when(employmentTermRepository.existsByWorkScheduleId(id)).thenReturn(true);

        assertThatThrownBy(() -> service().update(id, dto(day(DayOfWeek.MONDAY, "09:00", "18:00", 60))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_IN_USE);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("um horário que nunca foi atribuído edita-se à vontade")
    void permiteEditarHorarioNuncaAtribuido() {
        UUID id = UUID.randomUUID();
        WorkSchedule livre = new WorkSchedule();
        livre.setName("08–17");
        when(repository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(livre));
        when(employmentTermRepository.existsByWorkScheduleId(id)).thenReturn(false);
        when(repository.save(any(WorkSchedule.class))).thenAnswer(call -> call.getArgument(0));

        WorkSchedule after = service().update(id, dto(day(DayOfWeek.MONDAY, "09:00", "18:00", 30)));

        assertThat(after.getDays()).hasSize(1);
        assertThat(after.getDays().getFirst().getStartTime()).isEqualTo(LocalTime.parse("09:00"));
    }

    @Test
    @DisplayName("restaurar um horário que não está apagado é recusado")
    void recusaRestaurarHorarioVivo() {
        UUID id = UUID.randomUUID();
        WorkSchedule vivo = new WorkSchedule();
        vivo.setName("vivo");
        when(repository.findById(id)).thenReturn(Optional.of(vivo));

        assertThatThrownBy(() -> service().restore(id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_NOT_DELETED);
    }
}
