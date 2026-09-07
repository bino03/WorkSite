package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Um movimento de pagamento devolvido ao cliente.
 *
 * {@code proofUrl} é uma signed URL gerada na leitura — a chave de storage nunca
 * sai daqui. {@code allocations} tem uma linha por fatura coberta; no caso
 * normal é só uma.
 */
public record PaymentResponseDTO(
        UUID id,
        LocalDate paidOn,
        /** `NUMERARIO`, `MULTIBANCO`, `TRANSFERENCIA` ou `OUTRO` — o cliente traduz o rótulo. */
        String method,
        BigDecimal amount,
        String reference,
        String notes,
        String proofUrl,
        String proofFilename,
        UUID registeredBy,
        String registeredByName,
        OffsetDateTime registeredAt,
        List<PaymentAllocationDTO> allocations
) {}
