package com.management.managementapi.enterprises.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Um pagamento que um {@link SupplierReceipt} originou. Um recibo com faturas
 * em várias obras origina um pagamento por obra, por isso é junção e não
 * coluna. Só regista a origem: apagar o recibo não apaga o pagamento.
 */
@Entity
@Table(name = "supplier_receipt_payment", schema = "worksite")
@IdClass(SupplierReceiptPaymentId.class)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class SupplierReceiptPayment {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private SupplierReceipt receipt;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    public SupplierReceiptPayment() {
    }

    public SupplierReceiptPayment(SupplierReceipt receipt, Payment payment) {
        this.receipt = receipt;
        this.payment = payment;
    }

    public SupplierReceipt getReceipt() { return receipt; }
    public void setReceipt(SupplierReceipt receipt) { this.receipt = receipt; }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
}
