package com.management.managementapi.util;

import java.math.BigInteger;
import java.util.Map;

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

    /**
     * Código de banco português (dígitos 5-8) → nome. Espelha {@code PT_BANKS} em
     * {@code utils/iban.ts} do Backoffice — mudar um é mudar o outro. Só os bancos
     * de que se tem a certeza: um código fora da lista não leva nome, nunca um palpite.
     */
    private static final Map<String, String> PT_BANKS = Map.ofEntries(
            Map.entry("0007", "Novo Banco"),
            Map.entry("0010", "BPI"),
            Map.entry("0018", "Santander"),
            Map.entry("0019", "BBVA"),
            Map.entry("0023", "ActivoBank"),
            Map.entry("0033", "Millennium bcp"),
            Map.entry("0035", "CGD"),
            Map.entry("0036", "Banco Montepio"),
            Map.entry("0045", "Crédito Agrícola"),
            Map.entry("0061", "Banco BiG"),
            Map.entry("0079", "EuroBic"),
            Map.entry("0170", "ABANCA"),
            Map.entry("0186", "Banco Best"),
            Map.entry("0193", "Banco CTT"),
            Map.entry("0269", "Bankinter"),
            Map.entry("0781", "IGCP (Tesouro)"));

    /** O banco de um IBAN normalizado, ou null se não é português ou o código não está na lista. */
    public static String bankOf(String iban) {
        if (iban == null || iban.length() < 8 || !iban.startsWith("PT")) return null;
        return PT_BANKS.get(iban.substring(4, 8));
    }

    /** Agrupado de 4 em 4, como vem impresso: {@code PT50 0002 0123 …}. */
    public static String format(String iban) {
        return iban == null ? null : iban.replaceAll("(.{4})(?=.)", "$1 ");
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
