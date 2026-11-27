package com.management.managementapi.enterprises.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link InvoiceReceipt}: o par (recibo, fatura). */
public class InvoiceReceiptId implements Serializable {

    private UUID receipt;
    private UUID invoice;

    public InvoiceReceiptId() {
    }

    public InvoiceReceiptId(UUID receipt, UUID invoice) {
        this.receipt = receipt;
        this.invoice = invoice;
    }

    public UUID getReceipt() { return receipt; }
    public void setReceipt(UUID receipt) { this.receipt = receipt; }

    public UUID getInvoice() { return invoice; }
    public void setInvoice(UUID invoice) { this.invoice = invoice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvoiceReceiptId that)) return false;
        return Objects.equals(receipt, that.receipt) && Objects.equals(invoice, that.invoice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receipt, invoice);
    }
}
