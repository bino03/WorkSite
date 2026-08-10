package com.management.managementapi.enterprises.dto.invoice.response;

import java.util.List;

/**
 * Resultado de carregar uma fatura.
 *
 * O carregamento em massa é o caso normal — largam-se dez ficheiros de uma vez —
 * por isso a resposta tem de dizer, por ficheiro, o que correu bem e o que
 * merece atenção, sem interromper os restantes.
 *
 * @param invoice    a fatura criada; existe sempre, mesmo sem QR legível
 * @param qrRead     se o QR da AT foi descodificado. A false, os campos ficaram
 *                   por preencher e a fatura entra como "por rever" — é estado
 *                   normal (fornecedor estrangeiro, digitalização má), não erro
 * @param duplicates faturas do mesmo projeto com o mesmo ATCUD. Aviso, não
 *                   bloqueio: quem carregou é que sabe se foi engano
 * @param warnings   avisos do próprio QR — documento anulado na AT, nota de
 *                   crédito, campo ilegível
 */
public record InvoiceUploadResultDTO(
        ConstructionInvoiceResponseDTO invoice,
        boolean qrRead,
        List<DuplicateInvoiceRefDTO> duplicates,
        List<String> warnings
) {}
