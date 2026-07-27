package com.management.managementapi.dto.task.request;

import com.management.managementapi.model.enums.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateDTO(
    @NotNull TaskStatus status
) {}
