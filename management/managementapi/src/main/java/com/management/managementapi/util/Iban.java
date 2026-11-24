package com.management.managementapi.util;

import java.math.BigInteger;

/**
 * Normalização e validação de IBAN (ISO 13616): formato + dígitos de controlo
 * pelo mod-97.
 *
 * Não confirma o comprimento por país (PT são 25): o checksum já apanha um
 * dígito trocado ou dois adjacentes trocados, que é o erro que interessa
 * evitar antes de uma transferência, e uma tabela de comprimentos por país
 * seria mais uma coisa a manter.
 */
public final class Iban {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {}

    /** Sem espaços nem hífenes, em maiúsculas; null ou vazio → null. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase();
        return compact.isEmpty() ? null : compact;
    }

    /** Recebe o IBAN já normalizado. */
    public static boolean isValid(String iban) {
        if (iban == null || !iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
