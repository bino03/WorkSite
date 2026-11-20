package com.management.managementapi.dto.attendance.request;

import com.management.managementapi.model.enums.TimeDirection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * picar pelo QR. {@code direction} é o que a página mostrou ao funcionário ("Picar
 * ENTRADA"): o backend confere-o contra o estado atual e recusa se não bater
 * ({@code ATT_023}) — é o que impede um duplo toque, ou uma página aberta há uma hora,
 * de registar a picagem contrária.
 */
public record ClockRequestDTO(
        @NotBlank @Size(max = 64) String code,
        @NotNull TimeDirection direction
) {}
