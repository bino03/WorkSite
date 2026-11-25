package com.management.managementapi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IbanTest {

    @Test
    void normalizeStripsSpacesAndHyphensAndUppercases() {
        assertThat(Iban.normalize(" pt50 0002-0123 1234 5678 9015 4 ")).isEqualTo("PT50000201231234567890154");
        assertThat(Iban.normalize("   ")).isNull();
        assertThat(Iban.normalize(null)).isNull();
    }

    @Test
    void acceptsValidIbans() {
        assertThat(Iban.isValid("PT50000201231234567890154")).isTrue();
        assertThat(Iban.isValid("GB82WEST12345698765432")).isTrue();
        assertThat(Iban.isValid("DE89370400440532013000")).isTrue();
    }

    @Test
    void rejectsOneDigitChanged() {
        assertThat(Iban.isValid("PT50000201231234567890155")).isFalse();
    }

    @Test
    void rejectsTwoAdjacentDigitsSwapped() {
        assertThat(Iban.isValid("PT50000201231234567809154")).isFalse();
    }

    @Test
    void rejectsWrongShape() {
        assertThat(Iban.isValid("PT5")).isFalse();
        assertThat(Iban.isValid("5050000201231234567890154")).isFalse();
        assertThat(Iban.isValid("PT50 0002 0123 1234 5678 9015 4")).isFalse(); // não normalizado
        assertThat(Iban.isValid(null)).isFalse();
    }
}
