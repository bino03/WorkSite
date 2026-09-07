package com.management.managementapi.enterprises.model.enums;

/**
 * Como saiu o dinheiro. Mapeia o campo {@code Metodo Pagamento} do Excel da
 * Vilatro — ver docs/excel-parity.md §4. Texto que não encaixe nos três
 * primeiros entra como {@link #OUTRO}, com o original em {@code payment.notes}.
 */
public enum PaymentMethod {
    NUMERARIO,
    MULTIBANCO,
    TRANSFERENCIA,
    OUTRO
}
