package com.management.managementapi.enterprises.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;

class SupplierIbanTest {

    private static final String PT = "PT50000201231234567890154";
    private static final String DE = "DE89370400440532013000";

    @Test
    void storesEachIbanNormalizedInOrder() {
        assertThat(SupplierService.validIbans(List.of("de89 3704 0044 0532 0130 00", "pt50 0002 0123 1234 5678 9015 4")))
                .containsExactly(DE, PT);
    }

    @Test
    void dropsBlanksAndDuplicates() {
        assertThat(SupplierService.validIbans(Arrays.asList(PT, "", null, "PT50 0002 0123 1234 5678 9015 4", "  ")))
                .containsExactly(PT);
    }

    @Test
    void nullOrEmptyClearsThem() {
        assertThat(SupplierService.validIbans(null)).isEmpty();
        assertThat(SupplierService.validIbans(List.of())).isEmpty();
    }

    @Test
    void oneInvalidChecksumRejectsTheWholeSaveWithSupplier003() {
        assertThatThrownBy(() -> SupplierService.validIbans(List.of(DE, "PT50 0002 0123 1234 5678 9015 5")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SUPPLIER_IBAN_INVALID);
    }
}
