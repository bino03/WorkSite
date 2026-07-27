package com.management.managementapi.dto.task.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.management.managementapi.model.enums.TaskStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponseDTO(
    UUID id,
    String name,
    String description,
    OffsetDateTime dueDate,
    TaskStatus status,
    TaskAssigneeDTO createdBy,
    List<TaskAssigneeDTO> assignees,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
