package com.management.managementapi.enterprises.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Quanto de um {@link Payment} cobre uma {@link ConstructionInvoice}.
 *
 * Caso normal: uma linha com {@code amount = payment.amount}. As invariantes são
 * do serviço, não constraints:
 * <ul>
 *   <li>Σ({@code amount}) por pagamento = {@code payment.amount};</li>
 *   <li>por fatura, Σ({@code amount}) ≤ líquido da fatura.</li>
 * </ul>
 */
@Entity
@Table(name = "invoice_payment", schema = "worksite")
@IdClass(InvoicePaymentId.class)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class InvoicePayment {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private ConstructionInvoice invoice;

    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    public InvoicePayment() {
    }

    public InvoicePayment(Payment payment, ConstructionInvoice invoice, BigDecimal amount) {
        this.payment = payment;
        this.invoice = invoice;
        this.amount = amount;
    }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public ConstructionInvoice getInvoice() { return invoice; }
    public void setInvoice(ConstructionInvoice invoice) { this.invoice = invoice; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
