package com.management.managementapi.enterprises.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Chave composta de {@link InvoicePayment}: o par (pagamento, fatura). Os nomes
 * dos campos batem com os dos {@code @Id} da entidade e o tipo é o da PK das
 * entidades relacionadas ({@link UUID}), como o {@code @IdClass} exige.
 */
public class InvoicePaymentId implements Serializable {

    private UUID payment;
    private UUID invoice;

    public InvoicePaymentId() {
    }

    public InvoicePaymentId(UUID payment, UUID invoice) {
        this.payment = payment;
        this.invoice = invoice;
    }

    public UUID getPayment() { return payment; }
    public void setPayment(UUID payment) { this.payment = payment; }

    public UUID getInvoice() { return invoice; }
    public void setInvoice(UUID invoice) { this.invoice = invoice; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvoicePaymentId that)) return false;
        return Objects.equals(payment, that.payment) && Objects.equals(invoice, that.invoice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payment, invoice);
    }
}
