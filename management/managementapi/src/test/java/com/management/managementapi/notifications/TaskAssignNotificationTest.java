package com.management.managementapi.notifications;

import com.management.managementapi.dto.task.request.TaskUpsertDTO;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.mapper.task.TaskMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.Task;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.TaskRepository;
import com.management.managementapi.service.TaskService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Duas regras decididas a 2026-08-18, ambas sobre <b>quem não</b> é notificado:
 * quem já estava atribuído antes da edição, e quem fez a atribuição.
 *
 * Sem a primeira, cada gravação de uma tarefa renotificava toda a gente; sem a
 * segunda, atribuir uma tarefa a si próprio dava-se um aviso a si próprio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskAssignNotificationTest {

    @Mock private TaskRepository repository;
    @Mock private ProfileRepository profileRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private TaskMapper mapper;
    @Mock private NotificationService notifications;

    @InjectMocks private TaskService service;

    private static final UUID QUEM_ATRIBUI = UUID.randomUUID();
    private static final UUID JA_ATRIBUIDO = UUID.randomUUID();
    private static final UUID NOVO = UUID.randomUUID();

    private Profile profile(UUID id) {
        Profile p = new Profile();
        p.setId(id);
        return p;
    }

    private Task tarefaComAssignee(UUID assigneeId) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setName("Betonagem do piso 1");
        task.replaceAssignees(List.of(profile(assigneeId)));
        return task;
    }

    @Test
    @DisplayName("Na edição só é notificado quem passou a estar atribuído")
    void notifiesOnlyNewAssignees() {
        Task task = tarefaComAssignee(JA_ATRIBUIDO);
        when(repository.findById(any())).thenReturn(Optional.of(task));
        when(repository.save(any())).thenReturn(task);
        when(profileRepository.findAllById(any()))
                .thenReturn(List.of(profile(JA_ATRIBUIDO), profile(NOVO)));

        service.update(task.getId(),
                new TaskUpsertDTO("Betonagem do piso 1", null, null, null, Set.of(JA_ATRIBUIDO, NOVO)),
                QUEM_ATRIBUI, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> destinatarios = ArgumentCaptor.forClass(List.class);
        verify(notifications).notifyAll(destinatarios.capture(),
                eq(Notification.TYPE_TASK_ASSIGNED), anyString(), anyString(), anyString(), any());

        assertThat(destinatarios.getValue()).containsExactly(NOVO);
    }

    @Test
    @DisplayName("Quem faz a atribuição não é notificado, mesmo atribuindo-se a si próprio")
    void doesNotNotifyTheActor() {
        Task task = tarefaComAssignee(JA_ATRIBUIDO);
        when(repository.findById(any())).thenReturn(Optional.of(task));
        when(repository.save(any())).thenReturn(task);
        when(profileRepository.findAllById(any()))
                .thenReturn(List.of(profile(JA_ATRIBUIDO), profile(QUEM_ATRIBUI)));

        service.update(task.getId(),
                new TaskUpsertDTO("Betonagem do piso 1", null, null, null, Set.of(JA_ATRIBUIDO, QUEM_ATRIBUI)),
                QUEM_ATRIBUI, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> destinatarios = ArgumentCaptor.forClass(List.class);
        verify(notifications).notifyAll(destinatarios.capture(),
                eq(Notification.TYPE_TASK_ASSIGNED), anyString(), anyString(), anyString(), any());

        assertThat(destinatarios.getValue()).isEmpty();
    }
}
