package com.management.managementapi.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.task.request.TaskUpsertDTO;
import com.management.managementapi.dto.task.response.TaskResponseDTO;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.ForbiddenException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.task.TaskMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.Task;
import com.management.managementapi.model.enums.TaskStatus;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    private final TaskRepository repository;
    private final ProfileRepository profileRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final TaskMapper mapper;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> list(String q, TaskStatus status, UUID enterpriseId, UUID assigneeId,
                                       UUID requesterId, boolean isAdmin, Pageable pageable) {
        UUID effectiveAssigneeId = isAdmin ? assigneeId : requesterId;
        return repository.search(status, enterpriseId, effectiveAssigneeId, q, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Task getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.task(id.toString()));
    }

    /**
     * Devolve o DTO já mapeado dentro da transação — necessário porque
     * `spring.jpa.open-in-view` está desligado e associações lazy (createdBy,
     * enterprise, assignees) deixam de ter sessão Hibernate disponível assim
     * que o método transacional termina.
     */
    @Transactional(readOnly = true)
    public TaskResponseDTO getResponseById(UUID id, UUID requesterId, boolean isAdmin) {
        Task task = getById(id);
        validateAccess(task, requesterId, isAdmin);
        return mapper.toResponse(task);
    }

    public TaskResponseDTO create(TaskUpsertDTO dto, UUID createdByProfileId) {
        Profile creator = profileRepository.findById(createdByProfileId)
                .orElseThrow(() -> ResourceNotFoundException.profile(createdByProfileId.toString()));

        Task task = new Task();
        task.setName(dto.name());
        task.setDescription(dto.description());
        task.setDueDate(dto.dueDate());
        task.setCreatedBy(creator);
        task.setEnterprise(resolveEnterprise(dto.enterpriseId()));
        task.replaceAssignees(resolveAssignees(dto.assigneeIds()));

        Task saved = repository.save(task);
        notifyAssigned(saved, dto.assigneeIds(), Set.of(), createdByProfileId);
        return mapper.toResponse(saved);
    }

    /**
     * Atualiza a tarefa. Reservado a ADMIN ou a um dos utilizadores atribuídos
     * (validado aqui, não só no controller) — um EMPLOYEE não-atribuído não
     * pode editar a tarefa.
     */
    public TaskResponseDTO update(UUID id, TaskUpsertDTO dto, UUID requesterId, boolean isAdmin) {
        Task task = getById(id);
        validateAccess(task, requesterId, isAdmin);

        task.setName(dto.name());
        task.setDescription(dto.description());
        task.setDueDate(dto.dueDate());
        task.setEnterprise(resolveEnterprise(dto.enterpriseId()));

        // Lido ANTES do replace: depois já não há forma de saber quem é novo, e
        // renotificar quem já lá estava a cada gravação era ruído garantido.
        Set<UUID> antes = task.getAssignees().stream()
                .map(assignee -> assignee.getProfile().getId())
                .collect(Collectors.toSet());

        task.replaceAssignees(resolveAssignees(dto.assigneeIds()));

        Task saved = repository.save(task);
        notifyAssigned(saved, dto.assigneeIds(), antes, requesterId);
        return mapper.toResponse(saved);
    }

    /**
     * Atualiza o estado da tarefa. Reservado a ADMIN ou a um dos utilizadores
     * atribuídos (validado aqui, não só no controller).
     */
    public TaskResponseDTO updateStatus(UUID id, TaskStatus status, UUID requesterId, boolean isAdmin) {
        Task task = getById(id);
        validateAccess(task, requesterId, isAdmin);
        task.setStatus(status);
        return mapper.toResponse(repository.save(task));
    }

    public void delete(UUID id) {
        Task task = getById(id);
        repository.delete(task);
    }

    private void validateAccess(Task task, UUID requesterId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        boolean isAssignee = task.getAssignees().stream()
                .anyMatch(assignee -> assignee.getProfile().getId().equals(requesterId));
        if (!isAssignee) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Enterprise resolveEnterprise(UUID enterpriseId) {
        if (enterpriseId == null) {
            return null;
        }
        return enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));
    }

    /**
     * Avisa quem passou a ter a tarefa. Fora ficam dois casos, ambos por decisão
     * explícita: quem já estava atribuído antes (só os novos contam) e quem fez a
     * atribuição — atribuir uma tarefa a si próprio não se notifica a si próprio.
     */
    private void notifyAssigned(Task task, Set<UUID> assigneeIds, Set<UUID> jaAtribuidos, UUID actorId) {
        if (assigneeIds == null) return;

        List<UUID> novos = assigneeIds.stream()
                .filter(id -> !jaAtribuidos.contains(id))
                .filter(id -> !id.equals(actorId))
                .toList();

        notifications.notifyAll(novos, Notification.TYPE_TASK_ASSIGNED,
                "Nova tarefa atribuída",
                task.getName(),
                "/backoffice/tasks",
                task.getId());
    }

    private List<Profile> resolveAssignees(Set<UUID> assigneeIds) {
        List<Profile> profiles = profileRepository.findAllById(assigneeIds);
        if (profiles.size() != assigneeIds.size()) {
            throw new ResourceNotFoundException(ErrorCode.TASK_ASSIGNEE_NOT_FOUND);
        }
        return profiles;
    }
}
