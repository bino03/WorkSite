package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * O resultado de tentar registar um pagamento agregado.
 *
 * <ul>
 *   <li>{@code created == true}: o movimento bateu certo com a soma do que
 *       faltava pagar nas faturas escolhidas; {@code payment} traz-o e
 *       {@code leftOut} vem vazia.</li>
 *   <li>{@code created == false}: nada foi gravado. {@code selectedTotal} e
 *       {@code movementAmount} dizem por quanto não bate; se o movimento foi
 *       <b>menor</b>, {@code leftOut} lista as faturas a tirar da seleção para
 *       a soma acertar.</li>
 * </ul>
 */
public record AggregatePaymentResultDTO(
        boolean created,
        PaymentResponseDTO payment,
        List<LeftOutInvoiceDTO> leftOut,
        BigDecimal selectedTotal,
        BigDecimal movementAmount
) {}
