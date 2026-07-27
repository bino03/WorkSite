package com.management.managementapi.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.task.request.TaskStatusUpdateDTO;
import com.management.managementapi.dto.task.request.TaskUpsertDTO;
import com.management.managementapi.dto.task.response.TaskResponseDTO;
import com.management.managementapi.exeption.ForbiddenException;
import com.management.managementapi.model.Task;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.model.enums.TaskStatus;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.TaskService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping
    public Page<TaskResponseDTO> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) UUID assigneeId,
            @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {
        UUID requesterId = currentProfileId();
        return taskService.list(q, status, assigneeId, requesterId, authContext.isCurrentUserAdmin(), pageable);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> getById(@PathVariable UUID id) {
        UUID requesterId = currentProfileId();
        return ResponseEntity.ok(taskService.getResponseById(id, requesterId, authContext.isCurrentUserAdmin()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TaskResponseDTO> create(
            @Valid @RequestBody TaskUpsertDTO dto,
            HttpServletRequest request) {
        UUID creatorId = currentProfileId();
        TaskResponseDTO created = taskService.create(dto, creatorId);

        activityLogger.logCreate(creatorId, authContext.currentUserName().orElse("unknown"),
            EntityType.TASK, created.id(), created.name(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Um EMPLOYEE só consegue editar tarefas onde está atribuído — validado
     * no service (não só aqui).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpsertDTO dto,
            HttpServletRequest request) {
        UUID requesterId = currentProfileId();
        TaskResponseDTO updated = taskService.update(id, dto, requesterId, authContext.isCurrentUserAdmin());

        activityLogger.logEdit(requesterId, authContext.currentUserName().orElse("unknown"),
            EntityType.TASK, id, updated.name(), null, request);

        return ResponseEntity.ok(updated);
    }

    /**
     * Atualiza apenas o estado da tarefa. Um utilizador atribuído (não-admin)
     * só consegue chegar aqui — não pode alterar nome/descrição/prazo/atribuições.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponseDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TaskStatusUpdateDTO dto,
            HttpServletRequest request) {
        UUID requesterId = currentProfileId();
        TaskResponseDTO updated = taskService.updateStatus(id, dto.status(), requesterId, authContext.isCurrentUserAdmin());

        activityLogger.logEdit(requesterId, authContext.currentUserName().orElse("unknown"),
            EntityType.TASK, id, updated.name(), Map.of("status", dto.status()), request);

        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        Task task = taskService.getById(id);
        UUID adminId = currentProfileId();

        activityLogger.logDelete(adminId, authContext.currentUserName().orElse("unknown"),
            EntityType.TASK, id, task.getName(), request);

        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentProfileId() {
        return authContext.currentProfileId()
            .orElseThrow(() -> new ForbiddenException(ErrorCode.USER_NOT_AUTHENTICATED, "Utilizador não autenticado"));
    }
}
