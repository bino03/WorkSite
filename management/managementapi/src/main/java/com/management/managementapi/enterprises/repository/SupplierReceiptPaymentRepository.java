package com.management.managementapi.enterprises.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.SupplierReceiptPayment;
import com.management.managementapi.enterprises.model.SupplierReceiptPaymentId;

@Repository
public interface SupplierReceiptPaymentRepository extends JpaRepository<SupplierReceiptPayment, SupplierReceiptPaymentId> {

    /** Os pagamentos que um recibo originou. */
    List<SupplierReceiptPayment> findByReceiptId(UUID receiptId);
}
