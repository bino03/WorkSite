package com.management.managementapi.enterprises.dto.invoice.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Repartir uma fatura por rubricas — a operação que substitui o
 * {@code allocate} quando a folha não pertence toda ao mesmo sítio.
 *
 * Substitui a repartição atual por inteiro: as linhas que vêm aqui passam a ser
 * <b>as</b> despesas da fatura, e as que lá estavam desaparecem. É deliberado —
 * editar uma repartição é redesenhá-la, e um "acrescenta esta linha" deixaria a
 * soma a divergir do total sem ninguém dar por isso.
 *
 * A soma tem de bater certo com o total da fatura ({@code INVOICE_028}). A
 * exceção é a fatura que ainda não tem total: aí as linhas nascem a zero.
 *
 * Ver docs/faturas-modelo-alvo.md §7.
 */
public record InvoiceSplitDTO(

        @NotEmpty(message = "Indique pelo menos uma rubrica")
        @Valid
        List<InvoiceSplitLineDTO> lines
) {}
