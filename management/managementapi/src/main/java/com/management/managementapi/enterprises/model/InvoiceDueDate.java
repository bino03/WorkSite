package com.management.managementapi.enterprises.model;

import java.time.LocalDate;

/**
 * O vencimento de uma fatura e de onde vem.
 *
 * É derivado, nunca coluna: {@code data da fatura + prazo do fornecedor}, a
 * menos que a fatura tenha um vencimento escrito à mão, que prevalece. Mudar o
 * prazo de um fornecedor muda por isso o vencimento de todas as faturas dele.
 *
 * O {@link Basis} existe porque "sem vencimento" tem quatro causas diferentes
 * e cada uma resolve-se num sítio diferente — dizer só "sem prazo" deixava o
 * utilizador a adivinhar se falta o NIF na fatura ou o prazo no catálogo.
 *
 * @param dueDate null quando a fatura não tem vencimento
 */
public record InvoiceDueDate(LocalDate dueDate, Basis basis) {

    public enum Basis {
        /** Escrito à mão na fatura. */
        MANUAL,
        /** Data da fatura + prazo do fornecedor. */
        SUPPLIER_TERM,
        /** Nota de crédito: não se paga, não vence. */
        NOT_APPLICABLE,
        /** A fatura não tem NIF do fornecedor. */
        NO_SUPPLIER_NIF,
        /** O NIF da fatura não está no catálogo de fornecedores. */
        SUPPLIER_NOT_IN_CATALOG,
        /** O fornecedor está no catálogo mas não tem prazo definido. */
        NO_PAYMENT_TERM,
        /** Há prazo, mas a fatura não tem data de onde o contar. */
        NO_INVOICE_DATE
    }

    /**
     * @param supplier o fornecedor do catálogo com o NIF da fatura, ou null se
     *                 não houver (ou se a fatura não tiver NIF)
     */
    public static InvoiceDueDate of(ConstructionInvoice invoice, Supplier supplier) {
        if (invoice.getDocumentType() == ConstructionInvoice.DocumentType.CREDIT_NOTE) {
            return new InvoiceDueDate(null, Basis.NOT_APPLICABLE);
        }
        if (invoice.getDueDateOverride() != null) {
            return new InvoiceDueDate(invoice.getDueDateOverride(), Basis.MANUAL);
        }
        if (invoice.getSupplierNif() == null || invoice.getSupplierNif().isBlank()) {
            return new InvoiceDueDate(null, Basis.NO_SUPPLIER_NIF);
        }
        if (supplier == null) {
            return new InvoiceDueDate(null, Basis.SUPPLIER_NOT_IN_CATALOG);
        }
        if (supplier.getPaymentTermDays() == null) {
            return new InvoiceDueDate(null, Basis.NO_PAYMENT_TERM);
        }
        if (invoice.getInvoiceDate() == null) {
            return new InvoiceDueDate(null, Basis.NO_INVOICE_DATE);
        }
        return new InvoiceDueDate(
                invoice.getInvoiceDate().plusDays(supplier.getPaymentTermDays()), Basis.SUPPLIER_TERM);
    }
}
