package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Uma fatura que um pagamento cobre, e quanto desse movimento lhe toca. */
public record PaymentAllocationDTO(
        UUID invoiceId,
        String invoiceNumber,
        String supplierName,
        BigDecimal amount
) {}
