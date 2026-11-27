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
 * Quanto de um {@link SupplierReceipt} é para uma {@link ConstructionInvoice}.
 * Ao contrário de {@link InvoicePayment}, Σ({@code amount}) pode não bater com o
 * total do recibo (descontos, retenções): a diferença é só um aviso.
 */
@Entity
@Table(name = "invoice_receipt", schema = "worksite")
@IdClass(InvoiceReceiptId.class)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class InvoiceReceipt {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private SupplierReceipt receipt;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private ConstructionInvoice invoice;

    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    public InvoiceReceipt() {
    }

    public InvoiceReceipt(SupplierReceipt receipt, ConstructionInvoice invoice, BigDecimal amount) {
        this.receipt = receipt;
        this.invoice = invoice;
        this.amount = amount;
    }

    public SupplierReceipt getReceipt() { return receipt; }
    public void setReceipt(SupplierReceipt receipt) { this.receipt = receipt; }

    public ConstructionInvoice getInvoice() { return invoice; }
    public void setInvoice(ConstructionInvoice invoice) { this.invoice = invoice; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
