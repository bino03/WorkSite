package com.management.managementapi.dto.attendance.response;

/**
 * o que um pedido de férias ainda por gravar gastaria — para o aviso de saldo.
 *
 * @param year      o ano em que o pedido começa (é desse saldo que desconta)
 * @param days      dias úteis do pedido dentro desse ano
 * @param available o disponível desse ano <b>antes</b> do pedido
 */
public record VacationRequestPreviewDTO(int year, double days, double available) {}
