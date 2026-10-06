package com.management.managementapi.dto.attendance.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * um dia de trabalho de um horário. {@code weekday} é o {@link DayOfWeek} do Java
 * ("MONDAY".."SUNDAY"), não um número — o cliente não tem de saber a convenção
 * ISO que a base de dados guarda.
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

        @NotNull(message = "Os minutos de pausa são obrigatórios")
        @PositiveOrZero(message = "Os minutos de pausa não podem ser negativos")
        Integer breakMinutes
) {}
