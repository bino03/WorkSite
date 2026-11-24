package com.management.managementapi.enterprises.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;

class SupplierIbanTest {

    @Test
    void storesTheIbanNormalized() {
        assertThat(SupplierService.validIban("pt50 0002 0123 1234 5678 9015 4"))
                .isEqualTo("PT50000201231234567890154");
    }

    @Test
    void blankClearsTheIban() {
        assertThat(SupplierService.validIban("")).isNull();
        assertThat(SupplierService.validIban(null)).isNull();
    }

    @Test
    void invalidChecksumIsSupplier003() {
        assertThatThrownBy(() -> SupplierService.validIban("PT50 0002 0123 1234 5678 9015 5"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPLIER_IBAN_INVALID);
    }
}
