package com.management.managementapi.dto.task.response;

import java.util.UUID;

public record TaskEnterpriseSummaryDTO(
    UUID id,
    String name
) {}
