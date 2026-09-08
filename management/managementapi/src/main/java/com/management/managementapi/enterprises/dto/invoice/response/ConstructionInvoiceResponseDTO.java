package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;

/**
 * Fatura devolvida ao cliente.
 *
 * As URLs são sempre signed URLs geradas na leitura — a chave de storage nunca
 * sai daqui.
 *
 * <ul>
 *   <li>{@code thumbnailUrl} vem em todas as respostas: é o que a lista mostra.</li>
 *   <li>{@code fileUrl} só vem no detalhe ({@code GET /construction-invoices/{id}}).
 *       Assinar o documento completo de cada linha de uma lista de 20 seria
 *       trabalho deitado fora — quase nenhum é aberto.</li>
 * </ul>
 *
 * {@code allocated} e {@code needsReview} são derivados e não colunas: com uma
 * fatura por rubrica, um estado guardado só arriscava ficar dessincronizado.
 */
public record ConstructionInvoiceResponseDTO(
        UUID id,
        /** Nulo quando a fatura está na quarentena ou é uma despesa da empresa. */
        UUID enterpriseId,
        /** `PROJECT`, `COMPANY` ou `UNIDENTIFIED` — o cliente traduz o rótulo. */
        String scope,
        /** `INVOICE` ou `CREDIT_NOTE`. */
        String documentType,
        /** A fatura que esta nota de crédito credita; null numa fatura. */
        UUID relatedInvoiceId,
        /** `ARCHIVED`, `MISSING`, `TO_PRINT` ou `TO_REQUEST`. */
        String documentStatus,
        /** O que foi comprado — o "Produto/Serviço" do Excel. */
        String description,

        // ── identificação (QR da AT, corrigível à mão) ──
        String supplierName,
        String supplierNif,
        String invoiceNumber,
        String invoiceAtcud,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        String notes,

        // ── quarentena (só em UNIDENTIFIED) ──
        String possibleEnterprises,
        String askWhom,

        /** Falta a data ou o total — não dá para associar enquanto assim estiver. */
        boolean needsReview,

        // ── afetação à rubrica ──
        /** Tem pelo menos uma despesa. Não diz se está repartida a 100% — isso é o {@code allocationStatus}. */
        boolean allocated,
        /**
         * Os quatro campos seguintes descrevem a afetação <b>quando há
         * exatamente uma</b>, que é o caso normal e o que toda a UI já lê. Numa
         * fatura repartida por várias rubricas vêm a {@code null} de propósito:
         * mais vale a lista dizer "não sei em singular" do que apontar para a
         * primeira e mentir sobre as outras. A verdade completa é
         * {@code allocations}.
         */
        UUID expenseId,
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,
        /** Todas as rubricas por onde a fatura está repartida, da mais antiga para a mais recente. */
        List<InvoiceAllocationDTO> allocations,
        /**
         * {@code NONE} sem despesas · {@code COMPLETE} quando a soma bate certo
         * com o total · {@code PARTIAL} quando falta repartir · {@code PROVISIONAL}
         * numa fatura ainda sem total, cujas despesas nascem a zero (§7).
         */
        String allocationStatus,
        /** {@code total − Σ despesas}. Null quando a fatura ainda não tem total. */
        BigDecimal unallocatedAmount,

        // ── documento ──
        String fileUrl,
        String thumbnailUrl,
        String originalFilename,
        String mimeType,
        Long sizeBytes,
        /** Tamanho antes da compressão no browser; null quando o cliente não o reportou. */
        Long originalSizeBytes,
        UUID uploadedBy,
        String uploadedByName,
        OffsetDateTime uploadedAt,

        /**
         * Todos os ficheiros desta fatura, do mais antigo para o mais recente.
         * Vazia quando a fatura ainda não tem documento — estado legal desde a
         * V24. Os campos soltos acima descrevem o primeiro da lista.
         */
        List<InvoiceDocumentDTO> documents,

        // ── pagamento (fase 2) ──
        /** `UNPAID`, `PARTIAL` ou `PAID` — derivado, nunca coluna. O cliente traduz o rótulo. */
        String paymentStatus,
        /** Quanto já foi pago desta fatura. */
        BigDecimal paidAmount,
        /** O que há a pagar: total menos notas de crédito. Na fase 2 é igual ao total. */
        BigDecimal netAmount,
        /** Os movimentos que tocaram esta fatura, do mais antigo ao mais recente. */
        List<InvoicePaymentSummaryDTO> payments,

        // ── notas de crédito (fase 3) ──
        /** Σ do valor das notas de crédito ligadas a esta fatura (0 se não tiver). */
        BigDecimal creditNoteTotal,
        /** As notas de crédito ligadas a esta fatura, da mais recente para a mais antiga. */
        List<CreditNoteRefDTO> creditNotes,

        // ── contabilidade ──
        boolean sentToAccountant,
        UUID sentToAccountantBy,
        String sentToAccountantByName,
        /** `ADMIN` ou `EMPLOYEE` — o cliente traduz o rótulo. */
        String sentToAccountantByRole,
        OffsetDateTime sentToAccountantAt,

        UUID createdBy,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
