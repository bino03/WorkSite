package com.management.managementapi.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.task.request.TaskUpsertDTO;
import com.management.managementapi.dto.task.response.TaskResponseDTO;
import com.management.managementapi.exeption.ForbiddenException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.task.TaskMapper;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.Task;
import com.management.managementapi.model.enums.TaskStatus;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    private final TaskRepository repository;
    private final ProfileRepository profileRepository;
    private final TaskMapper mapper;

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> list(String q, TaskStatus status, UUID assigneeId,
                                       UUID requesterId, boolean isAdmin, Pageable pageable) {
        UUID effectiveAssigneeId = isAdmin ? assigneeId : requesterId;
        return repository.search(status, effectiveAssigneeId, q, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Task getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.task(id.toString()));
    }

    /**
     * Devolve o DTO já mapeado dentro da transação — necessário porque
     * `spring.jpa.open-in-view` está desligado e associações lazy (createdBy,
     * assignees) deixam de ter sessão Hibernate disponível assim que o método
     * transacional termina.
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
        task.replaceAssignees(resolveAssignees(dto.assigneeIds()));

        return mapper.toResponse(repository.save(task));
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
        task.replaceAssignees(resolveAssignees(dto.assigneeIds()));

        return mapper.toResponse(repository.save(task));
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

    private List<Profile> resolveAssignees(Set<UUID> assigneeIds) {
        List<Profile> profiles = profileRepository.findAllById(assigneeIds);
        if (profiles.size() != assigneeIds.size()) {
            throw new ResourceNotFoundException(ErrorCode.TASK_ASSIGNEE_NOT_FOUND);
        }
        return profiles;
    }
}
