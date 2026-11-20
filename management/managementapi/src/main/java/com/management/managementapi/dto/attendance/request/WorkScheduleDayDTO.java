package com.management.managementapi.dto.attendance.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;

/**
 * um dia de trabalho de um horário. {@code weekday} é o {@link DayOfWeek} do Java
 * ("MONDAY".."SUNDAY"), não um número — o cliente não tem de saber a convenção
 * ISO que a base de dados guarda.
 *
 * <p>{@code lunchStart}/{@code lunchEnd}: as duas ou nenhuma (dia de um só período).
 * Substituíram o {@code breakMinutes} a 2026-11-20 — o almoço pica-se, e o horário
 * diz quando devia ser.
 */
public record WorkScheduleDayDTO(

        @NotNull(message = "O dia da semana é obrigatório")
        DayOfWeek weekday,

        @NotNull(message = "A hora de entrada é obrigatória")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull(message = "A hora de saída é obrigatória")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime lunchStart,

        @JsonFormat(pattern = "HH:mm")
        LocalTime lunchEnd
) {}
