package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.attendance.request.EmploymentTermDTO;
import com.management.managementapi.dto.attendance.request.EmploymentUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.mapper.attendance.EmploymentMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.WorkSchedule;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.WorkScheduleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * O utilizador escolheu histórico com datas para que um relatório de janeiro
 * continue correto depois de lhe mudar o horário em março. Estes testes são sobre
 * a única mecânica que garante isso: mudar condições <b>fecha</b> o período em
 * vigor e <b>abre</b> um novo, em vez de reescrever o que lá estava.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmploymentServiceTest {

    @Mock private EmploymentRepository repository;
    @Mock private WorkScheduleRepository workScheduleRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private EmploymentMapper mapper;

    private static final UUID PERFIL = UUID.randomUUID();
    private static final UUID HORARIO_A = UUID.randomUUID();
    private static final UUID HORARIO_B = UUID.randomUUID();
    private static final LocalDate ADMISSAO = LocalDate.of(2026, 1, 1);

    private EmploymentService service() {
        Profile profile = new Profile();
        profile.setId(PERFIL);
        profile.setName("Funcionário");
        when(profileRepository.findById(PERFIL)).thenReturn(Optional.of(profile));

        when(workScheduleRepository.findByIdAndDeletedAtIsNull(any())).thenAnswer(call -> {
            WorkSchedule schedule = new WorkSchedule();
            schedule.setId(call.getArgument(0));
            schedule.setName("horário " + call.getArgument(0));
            return Optional.of(schedule);
        });
        when(repository.save(any(Employment.class))).thenAnswer(call -> call.getArgument(0));
        return new EmploymentService(repository, workScheduleRepository, profileRepository, mapper);
    }

    private Employment comPrimeiroPeriodo() {
        Employment employment = service().create(
                new EmploymentUpsertDTO(PERFIL, ADMISSAO, null, HORARIO_A, 22));
        when(repository.findByProfileId(PERFIL)).thenReturn(Optional.of(employment));
        return employment;
    }

    @Test
    @DisplayName("o primeiro período começa na data de admissão, com 22 dias de férias")
    void primeiroPeriodoComecaNaAdmissao() {
        Employment employment = service().create(
                new EmploymentUpsertDTO(PERFIL, ADMISSAO, null, HORARIO_A, 22));

        assertThat(employment.getTerms()).hasSize(1);
        assertThat(employment.currentTerm().getValidFrom()).isEqualTo(ADMISSAO);
        assertThat(employment.currentTerm().getValidTo()).isNull();
        assertThat(employment.currentTerm().getVacationDaysPerYear()).isEqualTo(22);
    }

    @Test
    @DisplayName("os dias de férias são editáveis por funcionário — 22 é só o default")
    void diasDeFeriasEditaveis() {
        Employment employment = service().create(
                new EmploymentUpsertDTO(PERFIL, ADMISSAO, null, HORARIO_A, 25));

        assertThat(employment.currentTerm().getVacationDaysPerYear()).isEqualTo(25);
    }

    @Test
    @DisplayName("mudar de horário fecha o período anterior no dia antes e abre um novo")
    void mudarDeHorarioFechaOAnterior() {
        comPrimeiroPeriodo();
        LocalDate mudanca = LocalDate.of(2026, 3, 1);

        Employment after = service().addTerm(PERFIL, new EmploymentTermDTO(HORARIO_B, 22, mudanca));

        assertThat(after.getTerms()).hasSize(2);
        assertThat(after.currentTerm().getValidFrom()).isEqualTo(mudanca);
        assertThat(after.currentTerm().getWorkSchedule().getId()).isEqualTo(HORARIO_B);

        assertThat(after.termOn(LocalDate.of(2026, 2, 28)).getValidTo())
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("um relatório de janeiro continua a usar o horário de janeiro depois da mudança em março")
    void oPassadoNaoMuda() {
        comPrimeiroPeriodo();

        Employment after = service().addTerm(PERFIL,
                new EmploymentTermDTO(HORARIO_B, 25, LocalDate.of(2026, 3, 1)));

        assertThat(after.termOn(LocalDate.of(2026, 1, 15)).getWorkSchedule().getId()).isEqualTo(HORARIO_A);
        assertThat(after.termOn(LocalDate.of(2026, 1, 15)).getVacationDaysPerYear()).isEqualTo(22);

        assertThat(after.termOn(LocalDate.of(2026, 6, 15)).getWorkSchedule().getId()).isEqualTo(HORARIO_B);
        assertThat(after.termOn(LocalDate.of(2026, 6, 15)).getVacationDaysPerYear()).isEqualTo(25);
    }

    @Test
    @DisplayName("o último dia de um período ainda é dia desse período")
    void intervaloFechadoNosDoisExtremos() {
        comPrimeiroPeriodo();
        LocalDate mudanca = LocalDate.of(2026, 3, 1);

        Employment after = service().addTerm(PERFIL, new EmploymentTermDTO(HORARIO_B, 22, mudanca));

        assertThat(after.termOn(LocalDate.of(2026, 2, 28)).getWorkSchedule().getId()).isEqualTo(HORARIO_A);
        assertThat(after.termOn(mudanca).getWorkSchedule().getId()).isEqualTo(HORARIO_B);
    }

    @Test
    @DisplayName("um período novo não pode começar antes da admissão")
    void recusaPeriodoAntesDaAdmissao() {
        comPrimeiroPeriodo();

        assertThatThrownBy(() -> service().addTerm(PERFIL,
                new EmploymentTermDTO(HORARIO_B, 22, LocalDate.of(2025, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMPLOYMENT_TERM_BEFORE_HIRE);
    }

    @Test
    @DisplayName("um período novo não pode começar no mesmo dia (ou antes) do que está em vigor")
    void recusaPeriodoNaoPosterior() {
        comPrimeiroPeriodo();

        assertThatThrownBy(() -> service().addTerm(PERFIL,
                new EmploymentTermDTO(HORARIO_B, 22, ADMISSAO)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMPLOYMENT_TERM_NOT_AFTER_CURRENT);
    }

    @Test
    @DisplayName("um funcionário não pode ter dois registos de emprego")
    void recusaEmpregoDuplicado() {
        when(repository.existsByProfileId(PERFIL)).thenReturn(true);

        assertThatThrownBy(() -> service().create(
                new EmploymentUpsertDTO(PERFIL, ADMISSAO, null, HORARIO_A, 22)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMPLOYMENT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("antes da admissão não há período nenhum — e por isso nem atraso nem falta")
    void antesDaAdmissaoNaoHaPeriodo() {
        Employment employment = comPrimeiroPeriodo();

        assertThat(employment.termOn(LocalDate.of(2025, 12, 31))).isNull();
    }
}
