package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.dto.attendance.request.TimeEntryUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.mapper.attendance.TimeEntryMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.attendance.TimeEntry;
import com.management.managementapi.model.attendance.TimeEntryRevision;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntryChange;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;
import com.management.managementapi.repository.attendance.TimeEntryRepository;
import com.management.managementapi.repository.attendance.TimeEntryRevisionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Duas coisas aqui não são negociáveis e por isso têm testes.
 *
 * <p>A <b>sequência</b>: uma entrada é seguida de uma saída. A regra vive no
 * service e não num CHECK porque um CHECK não consegue exprimir sequência — logo
 * se ela se perder, nada a apanha.
 *
 * <p>A <b>revisão tirada antes da alteração</b>: se a fotografia for tirada depois
 * de mexer na entidade, guarda o estado novo e a tabela de auditoria passa a
 * mentir sem que nada falhe. É o tipo de erro que só se vê numa auditoria, anos
 * depois.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeEntryServiceTest {

    @Mock private TimeEntryRepository repository;
    @Mock private TimeEntryRevisionRepository revisionRepository;
    @Mock private EmploymentRepository employmentRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private TimeEntryMapper mapper;

    private static final UUID FUNCIONARIO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final AttendanceZone ZONE = new AttendanceZone("Europe/Lisbon");

    private TimeEntryService service() {
        Profile profile = new Profile();
        profile.setId(FUNCIONARIO);
        profile.setName("Funcionário");
        when(profileRepository.findById(any())).thenReturn(Optional.of(profile));
        when(repository.save(any(TimeEntry.class))).thenAnswer(call -> {
            TimeEntry entry = call.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
            return entry;
        });
        return new TimeEntryService(repository, revisionRepository, employmentRepository,
                profileRepository, enterpriseRepository, mapper, ZONE);
    }

    /** 2026-07-15 é verão: Lisboa está em UTC+1, logo 08:00 local = 07:00Z. */
    private static OffsetDateTime horaLocal(int hora, int minuto) {
        return OffsetDateTime.of(2026, 7, 15, hora - 1, minuto, 0, 0, ZoneOffset.UTC);
    }

    private static TimeEntry existente(TimeDirection direction, int hora) {
        TimeEntry entry = new TimeEntry();
        entry.setId(UUID.randomUUID());
        Profile profile = new Profile();
        profile.setId(FUNCIONARIO);
        profile.setName("Funcionário");
        entry.setProfile(profile);
        entry.setDirection(direction);
        entry.setHappenedAt(horaLocal(hora, 0));
        return entry;
    }

    private static TimeEntryUpsertDTO dto(TimeDirection direction, int hora, int minuto) {
        return new TimeEntryUpsertDTO(FUNCIONARIO, null, horaLocal(hora, minuto), direction, null, null);
    }

    private void diaTem(TimeEntry... entries) {
        when(repository.findForProfileBetween(eq(FUNCIONARIO), any(), any())).thenReturn(List.of(entries));
    }

    @Test
    @DisplayName("a primeira picagem do dia tem de ser uma entrada")
    void primeiraPicagemEhEntrada() {
        diaTem();

        assertThatThrownBy(() -> service().register(dto(TimeDirection.OUT, 17, 0), ADMIN, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TIME_ENTRY_OUT_OF_SEQUENCE);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("entrada e depois saída é aceite")
    void entradaDepoisSaida() {
        diaTem(existente(TimeDirection.IN, 8));

        assertThatCode(() -> service().register(dto(TimeDirection.OUT, 17, 0), ADMIN, "Admin"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("duas entradas seguidas são recusadas")
    void recusaDuasEntradasSeguidas() {
        diaTem(existente(TimeDirection.IN, 8));

        assertThatThrownBy(() -> service().register(dto(TimeDirection.IN, 9, 0), ADMIN, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TIME_ENTRY_OUT_OF_SEQUENCE);
    }

    @Test
    @DisplayName("uma picagem inserida no meio tem de respeitar a sequência dos dois lados")
    void validaPicagemInseridaNoMeio() {
        // Dia com 08:00 IN e 17:00 OUT. Inserir uma entrada às 12:00 daria IN,IN,OUT.
        diaTem(existente(TimeDirection.IN, 8), existente(TimeDirection.OUT, 17));

        assertThatThrownBy(() -> service().register(dto(TimeDirection.IN, 12, 0), ADMIN, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TIME_ENTRY_OUT_OF_SEQUENCE);
    }

    @Test
    @DisplayName("uma saída ao almoço e uma entrada depois são aceites — IN,OUT,IN,OUT")
    void aceitaIntervaloAoAlmoco() {
        diaTem(existente(TimeDirection.IN, 8), existente(TimeDirection.OUT, 12));

        assertThatCode(() -> service().register(dto(TimeDirection.IN, 13, 0), ADMIN, "Admin"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("duas picagens no mesmo instante são recusadas")
    void recusaInstanteDuplicado() {
        diaTem(existente(TimeDirection.IN, 8));

        assertThatThrownBy(() -> service().register(dto(TimeDirection.OUT, 8, 0), ADMIN, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TIME_ENTRY_DUPLICATE_INSTANT);
    }

    @Test
    @DisplayName("uma picagem antes da data de admissão é recusada")
    void recusaPicagemAntesDaAdmissao() {
        Employment employment = new Employment();
        employment.setHiredAt(LocalDate.of(2026, 8, 1));
        when(employmentRepository.findByProfileId(FUNCIONARIO)).thenReturn(Optional.of(employment));
        diaTem();

        assertThatThrownBy(() -> service().register(dto(TimeDirection.IN, 8, 0), ADMIN, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TIME_ENTRY_BEFORE_HIRE);
    }

    @Test
    @DisplayName("sem dados de emprego não se valida a admissão — registar é possível antes de criar a ficha")
    void semEmpregoNaoValidaAdmissao() {
        when(employmentRepository.findByProfileId(FUNCIONARIO)).thenReturn(Optional.empty());
        diaTem();

        assertThatCode(() -> service().register(dto(TimeDirection.IN, 8, 0), ADMIN, "Admin"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("corrigir guarda na revisão o estado ANTERIOR, não o novo")
    void revisaoGuardaOEstadoAnterior() {
        TimeEntry entry = existente(TimeDirection.IN, 8);
        entry.setNote("nota antiga");
        when(repository.findByIdAndDeletedAtIsNull(entry.getId())).thenReturn(Optional.of(entry));
        diaTem(entry);

        service().correct(entry.getId(),
                new TimeEntryUpsertDTO(FUNCIONARIO, null, horaLocal(9, 30), TimeDirection.IN,
                        "nota nova", "esqueci-me de picar"),
                ADMIN, "Admin");

        ArgumentCaptor<TimeEntryRevision> captor = ArgumentCaptor.forClass(TimeEntryRevision.class);
        verify(revisionRepository).save(captor.capture());
        TimeEntryRevision revision = captor.getValue();

        assertThat(revision.getChange()).isEqualTo(TimeEntryChange.UPDATE);
        assertThat(revision.getChangedByName()).isEqualTo("Admin");
        assertThat(revision.getReason()).isEqualTo("esqueci-me de picar");
        assertThat(revision.getPreviousNote()).isEqualTo("nota antiga");
        assertThat(revision.getPreviousHappenedAt()).isEqualTo(horaLocal(8, 0));
        // E a picagem ficou de facto com os valores novos.
        assertThat(entry.getNote()).isEqualTo("nota nova");
        assertThat(entry.getHappenedAt()).isEqualTo(horaLocal(9, 30));
    }

    @Test
    @DisplayName("anular uma picagem é soft-delete e deixa revisão")
    void anularEhSoftDelete() {
        TimeEntry entry = existente(TimeDirection.IN, 8);
        when(repository.findByIdAndDeletedAtIsNull(entry.getId())).thenReturn(Optional.of(entry));

        service().softDelete(entry.getId(), "picada na pessoa errada", ADMIN, "Admin");

        assertThat(entry.getDeletedAt()).isNotNull();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());

        ArgumentCaptor<TimeEntryRevision> captor = ArgumentCaptor.forClass(TimeEntryRevision.class);
        verify(revisionRepository).save(captor.capture());
        assertThat(captor.getValue().getChange()).isEqualTo(TimeEntryChange.DELETE);
        assertThat(captor.getValue().getPreviousDeletedAt()).isNull();
    }

    @Test
    @DisplayName("restaurar uma picagem que não está anulada é recusado")
    void recusaRestaurarPicagemViva() {
        TimeEntry entry = existente(TimeDirection.IN, 8);
        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service().restore(entry.getId(), null, ADMIN, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TIME_ENTRY_NOT_DELETED);
    }

    @Test
    @DisplayName("registar grava uma revisão CREATE sem estado anterior")
    void registarGravaRevisaoCreate() {
        diaTem();

        service().register(dto(TimeDirection.IN, 8, 0), ADMIN, "Admin");

        ArgumentCaptor<TimeEntryRevision> captor = ArgumentCaptor.forClass(TimeEntryRevision.class);
        verify(revisionRepository).save(captor.capture());
        TimeEntryRevision revision = captor.getValue();

        assertThat(revision.getChange()).isEqualTo(TimeEntryChange.CREATE);
        assertThat(revision.getPreviousHappenedAt()).isNull();
        assertThat(revision.getPreviousDirection()).isNull();
    }
}
