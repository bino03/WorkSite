package com.management.managementapi.dto.task.response;

import java.util.UUID;

public record TaskAssigneeDTO(
    UUID id,
    String name
) {}
