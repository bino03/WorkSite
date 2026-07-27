package com.management.managementapi.dto.task.request;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskUpsertDTO(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotNull OffsetDateTime dueDate,
    UUID enterpriseId,
    @NotEmpty Set<UUID> assigneeIds
) {}
