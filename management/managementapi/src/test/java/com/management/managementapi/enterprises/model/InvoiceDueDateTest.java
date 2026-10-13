package com.management.managementapi.enterprises.model;

import com.management.managementapi.enterprises.model.InvoiceDueDate.Basis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O vencimento é a data da fatura mais o prazo do fornecedor, a menos que a
 * fatura tenha um escrito à mão — e, quando não há vencimento, tem de se saber
 * porquê, porque cada causa resolve-se num sítio diferente.
 */
class InvoiceDueDateTest {

    private static final LocalDate INVOICE_DATE = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("30 dias no fornecedor dão o vencimento 30 dias depois da data da fatura")
    void addsSupplierTermToInvoiceDate() {
        InvoiceDueDate due = InvoiceDueDate.of(invoice("502693134", INVOICE_DATE), supplier(30));

        assertThat(due.dueDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(due.basis()).isEqualTo(Basis.SUPPLIER_TERM);
    }

    @Test
    @DisplayName("prazo 0 é pronto pagamento: vence no dia da fatura, não fica sem prazo")
    void zeroTermIsDueOnInvoiceDate() {
        InvoiceDueDate due = InvoiceDueDate.of(invoice("502693134", INVOICE_DATE), supplier(0));

        assertThat(due.dueDate()).isEqualTo(INVOICE_DATE);
        assertThat(due.basis()).isEqualTo(Basis.SUPPLIER_TERM);
    }

    @Test
    @DisplayName("o vencimento manual prevalece sobre o prazo do fornecedor")
    void manualOverrideWins() {
        ConstructionInvoice invoice = invoice("502693134", INVOICE_DATE);
        invoice.setDueDateOverride(LocalDate.of(2026, 12, 15));

        InvoiceDueDate due = InvoiceDueDate.of(invoice, supplier(30));

        assertThat(due.dueDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(due.basis()).isEqualTo(Basis.MANUAL);
    }

    @Test
    @DisplayName("o vencimento manual dá vencimento a uma fatura sem NIF, como as importadas do Excel")
    void manualOverrideWorksWithoutNif() {
        ConstructionInvoice invoice = invoice(null, INVOICE_DATE);
        invoice.setDueDateOverride(LocalDate.of(2026, 11, 5));

        InvoiceDueDate due = InvoiceDueDate.of(invoice, null);

        assertThat(due.dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(due.basis()).isEqualTo(Basis.MANUAL);
    }

    @Test
    @DisplayName("fornecedor sem prazo: a fatura fica sem vencimento — não há prazo por omissão")
    void supplierWithoutTermHasNoDueDate() {
        InvoiceDueDate due = InvoiceDueDate.of(invoice("502693134", INVOICE_DATE), supplier(null));

        assertThat(due.dueDate()).isNull();
        assertThat(due.basis()).isEqualTo(Basis.NO_PAYMENT_TERM);
    }

    @Test
    @DisplayName("fatura sem NIF, ou só com espaços, não tem vencimento")
    void noNifHasNoDueDate() {
        assertThat(InvoiceDueDate.of(invoice(null, INVOICE_DATE), null).basis())
                .isEqualTo(Basis.NO_SUPPLIER_NIF);
        assertThat(InvoiceDueDate.of(invoice("  ", INVOICE_DATE), null).basis())
                .isEqualTo(Basis.NO_SUPPLIER_NIF);
    }

    @Test
    @DisplayName("NIF fora do catálogo não tem vencimento, e diz que é o catálogo que falta")
    void unknownSupplierHasNoDueDate() {
        InvoiceDueDate due = InvoiceDueDate.of(invoice("999999990", INVOICE_DATE), null);

        assertThat(due.dueDate()).isNull();
        assertThat(due.basis()).isEqualTo(Basis.SUPPLIER_NOT_IN_CATALOG);
    }

    @Test
    @DisplayName("com prazo mas sem data da fatura não há de onde contar")
    void noInvoiceDateHasNoDueDate() {
        InvoiceDueDate due = InvoiceDueDate.of(invoice("502693134", null), supplier(30));

        assertThat(due.dueDate()).isNull();
        assertThat(due.basis()).isEqualTo(Basis.NO_INVOICE_DATE);
    }

    @Test
    @DisplayName("uma nota de crédito não vence, mesmo com fornecedor a 30 dias")
    void creditNoteNeverHasDueDate() {
        ConstructionInvoice creditNote = invoice("502693134", INVOICE_DATE);
        creditNote.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        creditNote.setRelatedInvoiceId(UUID.randomUUID());

        InvoiceDueDate due = InvoiceDueDate.of(creditNote, supplier(30));

        assertThat(due.dueDate()).isNull();
        assertThat(due.basis()).isEqualTo(Basis.NOT_APPLICABLE);
    }

    private static ConstructionInvoice invoice(String supplierNif, LocalDate invoiceDate) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setSupplierNif(supplierNif);
        invoice.setInvoiceDate(invoiceDate);
        return invoice;
    }

    private static Supplier supplier(Integer paymentTermDays) {
        Supplier supplier = new Supplier();
        supplier.setNif("502693134");
        supplier.setName("Brivel");
        supplier.setPaymentTermDays(paymentTermDays);
        return supplier;
    }
}
