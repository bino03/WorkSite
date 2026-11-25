package com.management.managementapi.service.attendance;

import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;

import com.management.managementapi.dto.attendance.request.AbsenceUpsertDTO;
import com.management.managementapi.dto.attendance.request.MyVacationRequestDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.Employment;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.AbsenceDocumentRepository;
import com.management.managementapi.repository.attendance.AbsenceRepository;
import com.management.managementapi.repository.attendance.EmploymentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * As regras das ausências. Duas merecem destaque:
 *
 * <p><b>Sobreposição recusada na marcação.</b> Duas ausências sobrepostas não têm
 * significado — qual delas explicaria o dia? Melhor recusar à entrada do que deixar
 * o cálculo escolher uma arbitrariamente.
 *
 * <p><b>Validar antes de subir.</b> Subir primeiro e validar depois deixa órfãos no
 * bucket, que foi o que aconteceu com as faturas em 2026-09-18.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbsenceServiceTest {

    @Mock private AbsenceRepository repository;
    @Mock private AbsenceDocumentRepository documentRepository;
    @Mock private EmploymentRepository employmentRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private NotificationService notifications;

    private static final UUID PERFIL = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private AbsenceService service() {
        Profile profile = new Profile();
        profile.setId(PERFIL);
        profile.setName("Funcionário");
        when(profileRepository.findById(any())).thenReturn(Optional.of(profile));
        when(repository.save(any(Absence.class))).thenAnswer(call -> {
            Absence absence = call.getArgument(0);
            if (absence.getId() == null) {
                absence.setId(UUID.randomUUID());
            }
            return absence;
        });
        return new AbsenceService(repository, documentRepository, employmentRepository,
                profileRepository, storageService, notifications);
    }

    // ── Pedido pelo próprio (fase 5b) ─────────────────────────

    @Test
    @DisplayName("o funcionário pede férias para si: nasce pendente, é VACATION, e os ADMIN são avisados")
    void pedidoProprioAvisaAdmins() {
        semSobreposicao();
        when(profileRepository.findIdsByRoleInAndAccountStatus(any(), eq(AccountStatus.unlocked)))
                .thenReturn(List.of(ADMIN, PERFIL));

        Absence absence = service().requestVacation(PERFIL,
                new MyVacationRequestDTO(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), null, null));

        assertThat(absence.getStatus()).isEqualTo(AbsenceStatus.PENDING);
        assertThat(absence.getType()).isEqualTo(AbsenceType.VACATION);
        assertThat(absence.getProfile().getId()).isEqualTo(PERFIL);
        // Um ADMIN que pede para si próprio não se avisa a si mesmo.
        verify(notifications).notifyAll(eq(List.of(ADMIN)), eq(Notification.TYPE_ABSENCE_REQUESTED), any(),
                eq("Funcionário pediu férias de 13/07/2026 a 17/07/2026."), eq("/team/absences"), eq(absence.getId()));
    }

    private Absence existente(UUID dono, AbsenceStatus status) {
        Profile profile = new Profile();
        profile.setId(dono);
        profile.setName("Dono");
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        absence.setProfile(profile);
        absence.setType(AbsenceType.VACATION);
        absence.setStartsOn(LocalDate.of(2026, 8, 3));
        absence.setEndsOn(LocalDate.of(2026, 8, 3));
        absence.setHalfDay(AbsenceHalfDay.MORNING);
        absence.setStatus(status);
        when(repository.findByIdAndDeletedAtIsNull(absence.getId())).thenReturn(Optional.of(absence));
        return absence;
    }

    @Test
    @DisplayName("anula-se o próprio pedido pendente; o de outro dá 'não encontrada'; um decidido não")
    void anularProprioPedido() {
        Absence meu = existente(PERFIL, AbsenceStatus.PENDING);
        service().cancelOwnRequest(meu.getId(), PERFIL);
        assertThat(meu.getDeletedAt()).isNotNull();

        Absence alheio = existente(UUID.randomUUID(), AbsenceStatus.PENDING);
        assertThatThrownBy(() -> service().cancelOwnRequest(alheio.getId(), PERFIL))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(alheio.getDeletedAt()).isNull();

        Absence decidido = existente(PERFIL, AbsenceStatus.APPROVED);
        assertThatThrownBy(() -> service().cancelOwnRequest(decidido.getId(), PERFIL))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("decidir avisa o funcionário, com o dia e o meio dia")
    void decidirAvisaOFuncionario() {
        Absence pedido = existente(PERFIL, AbsenceStatus.PENDING);

        service().decide(pedido.getId(), false, ADMIN);

        verify(notifications).notify(eq(PERFIL), eq(Notification.TYPE_ABSENCE_DECIDED), eq("Pedido recusado"),
                eq("O seu pedido de férias a 03/08/2026 (manhã) foi recusado."), eq("/backoffice/ferias"),
                eq(pedido.getId()));
    }

    private static AbsenceUpsertDTO dto(LocalDate from, LocalDate to, AbsenceHalfDay halfDay) {
        return new AbsenceUpsertDTO(PERFIL, AbsenceType.VACATION, from, to, halfDay, null);
    }

    private void semSobreposicao() {
        when(repository.findForProfileOverlapping(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("uma ausência nasce sempre pendente — aprovar é um passo à parte")
    void nasceSemprePendente() {
        semSobreposicao();

        Absence absence = service().create(
                dto(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), null), ADMIN);

        assertThat(absence.getStatus()).isEqualTo(AbsenceStatus.PENDING);
        assertThat(absence.getHalfDay()).isEqualTo(AbsenceHalfDay.NONE);
        assertThat(absence.getApprovedAt()).isNull();
    }

    @Test
    @DisplayName("data de fim antes do início é recusada")
    void recusaDatasInvertidas() {
        semSobreposicao();

        assertThatThrownBy(() -> service().create(
                dto(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 13), null), ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_INVALID_DATES);
    }

    @Test
    @DisplayName("meio dia num intervalo de vários dias é recusado — não quer dizer nada")
    void recusaMeioDiaEmIntervalo() {
        semSobreposicao();

        assertThatThrownBy(() -> service().create(
                dto(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), AbsenceHalfDay.MORNING), ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_HALF_DAY_ON_RANGE);
    }

    @Test
    @DisplayName("meio dia num dia só é aceite")
    void aceitaMeioDiaNumDiaSo() {
        semSobreposicao();
        LocalDate dia = LocalDate.of(2026, 7, 15);

        Absence absence = service().create(dto(dia, dia, AbsenceHalfDay.AFTERNOON), ADMIN);

        assertThat(absence.getHalfDay()).isEqualTo(AbsenceHalfDay.AFTERNOON);
    }

    @Test
    @DisplayName("duas ausências sobrepostas são recusadas")
    void recusaSobreposicao() {
        Absence existente = new Absence();
        existente.setId(UUID.randomUUID());
        existente.setStatus(AbsenceStatus.APPROVED);
        when(repository.findForProfileOverlapping(any(), any(), any(), any()))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service().create(
                dto(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), null), ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_OVERLAPS);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("uma ausência recusada não impede marcar outra nas mesmas datas")
    void recusadaNaoBloqueia() {
        Absence recusada = new Absence();
        recusada.setId(UUID.randomUUID());
        recusada.setStatus(AbsenceStatus.REJECTED);
        when(repository.findForProfileOverlapping(any(), any(), any(), any()))
                .thenReturn(List.of(recusada));

        Absence nova = service().create(
                dto(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), null), ADMIN);

        assertThat(nova.getStatus()).isEqualTo(AbsenceStatus.PENDING);
    }

    @Test
    @DisplayName("aprovar grava quem aprovou e quando")
    void aprovarGravaQuemEQuando() {
        Absence pendente = existente(PERFIL, AbsenceStatus.PENDING);

        Absence aprovada = service().decide(pendente.getId(), true, ADMIN);

        assertThat(aprovada.getStatus()).isEqualTo(AbsenceStatus.APPROVED);
        assertThat(aprovada.getApprovedAt()).isNotNull();
        assertThat(aprovada.getApprovedBy()).isNotNull();
    }

    @Test
    @DisplayName("aprovar duas vezes é recusado")
    void recusaDecidirDuasVezes() {
        Absence aprovada = new Absence();
        aprovada.setId(UUID.randomUUID());
        aprovada.setStatus(AbsenceStatus.APPROVED);
        when(repository.findByIdAndDeletedAtIsNull(aprovada.getId())).thenReturn(Optional.of(aprovada));

        assertThatThrownBy(() -> service().decide(aprovada.getId(), true, ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("justificar uma falta cria uma ausência já aprovada que cobre o dia")
    void justificarCriaAusenciaAprovada() {
        semSobreposicao();
        LocalDate dia = LocalDate.of(2026, 7, 15);

        Absence absence = service().justifyDay(PERFIL, dia, AbsenceType.JUSTIFIED, "foi ao médico", ADMIN);

        assertThat(absence.getStatus()).isEqualTo(AbsenceStatus.APPROVED);
        assertThat(absence.getStartsOn()).isEqualTo(dia);
        assertThat(absence.getEndsOn()).isEqualTo(dia);
        assertThat(absence.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("uma ausência antes da admissão é recusada")
    void recusaAusenciaAntesDaAdmissao() {
        Employment employment = new Employment();
        employment.setHiredAt(LocalDate.of(2026, 8, 1));
        when(employmentRepository.findByProfileId(PERFIL)).thenReturn(Optional.of(employment));
        semSobreposicao();

        assertThatThrownBy(() -> service().create(
                dto(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), null), ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_BEFORE_HIRE);
    }

    @Test
    @DisplayName("anular uma ausência é soft-delete")
    void anularEhSoftDelete() {
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        absence.setStatus(AbsenceStatus.APPROVED);
        when(repository.findByIdAndDeletedAtIsNull(absence.getId())).thenReturn(Optional.of(absence));

        service().softDelete(absence.getId());

        assertThat(absence.getDeletedAt()).isNotNull();
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("um justificativo de tipo não permitido é recusado SEM chegar ao Storage")
    void recusaTipoSemSubir() {
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        when(repository.findByIdAndDeletedAtIsNull(absence.getId())).thenReturn(Optional.of(absence));

        MockMultipartFile ficheiro = new MockMultipartFile(
                "file", "baixa.exe", "application/x-msdownload", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> service().attachDocument(absence.getId(), ficheiro))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ABSENCE_DOCUMENT_TYPE);

        // O essencial: nada subiu. Validar depois de subir deixa órfãos no bucket.
        verifyNoInteractions(storageService);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("um ficheiro vazio é recusado sem chegar ao Storage")
    void recusaFicheiroVazio() {
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        when(repository.findByIdAndDeletedAtIsNull(absence.getId())).thenReturn(Optional.of(absence));

        MockMultipartFile vazio = new MockMultipartFile(
                "file", "baixa.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service().attachDocument(absence.getId(), vazio))
                .isInstanceOf(FileUploadException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("um PDF válido sobe com a chave no prefixo da ausência e guarda bucket+key")
    void sobePdfValido() {
        Absence absence = new Absence();
        absence.setId(UUID.randomUUID());
        when(repository.findByIdAndDeletedAtIsNull(absence.getId())).thenReturn(Optional.of(absence));
        when(storageService.sanitizeFileName(any())).thenReturn("baixa.pdf");
        when(documentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MockMultipartFile ficheiro = new MockMultipartFile(
                "file", "baixa.pdf", "application/pdf", new byte[] { 1, 2, 3, 4 });

        var document = service().attachDocument(absence.getId(), ficheiro);

        assertThat(document.getBucket()).isEqualTo("documents");
        assertThat(document.getStorageKey())
                .startsWith("attendance/absence/" + absence.getId() + "/")
                .endsWith("_baixa.pdf");
        assertThat(document.getSizeBytes()).isEqualTo(4);
        assertThat(document.getMimeType()).isEqualTo("application/pdf");
    }
}
