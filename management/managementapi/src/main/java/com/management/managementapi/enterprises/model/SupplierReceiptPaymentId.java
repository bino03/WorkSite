package com.management.managementapi.enterprises.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link SupplierReceiptPayment}: o par (recibo, pagamento). */
public class SupplierReceiptPaymentId implements Serializable {

    private UUID receipt;
    private UUID payment;

    public SupplierReceiptPaymentId() {
    }

    public SupplierReceiptPaymentId(UUID receipt, UUID payment) {
        this.receipt = receipt;
        this.payment = payment;
    }

    public UUID getReceipt() { return receipt; }
    public void setReceipt(UUID receipt) { this.receipt = receipt; }

    public UUID getPayment() { return payment; }
    public void setPayment(UUID payment) { this.payment = payment; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplierReceiptPaymentId that)) return false;
        return Objects.equals(receipt, that.receipt) && Objects.equals(payment, that.payment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receipt, payment);
    }
}
