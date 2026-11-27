package com.management.managementapi.enterprises.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.InvoiceReceipt;
import com.management.managementapi.enterprises.model.InvoiceReceiptId;

@Repository
public interface InvoiceReceiptRepository extends JpaRepository<InvoiceReceipt, InvoiceReceiptId> {

    /** Ligações de uma fatura — os recibos que aparecem no detalhe dela. */
    List<InvoiceReceipt> findByInvoiceId(UUID invoiceId);

    /** Ligações de um recibo. */
    List<InvoiceReceipt> findByReceiptId(UUID receiptId);

    /** Todas as ligações de um conjunto de recibos — para o "também cobre". */
    List<InvoiceReceipt> findByReceiptIdIn(Collection<UUID> receiptIds);

    /** Apaga as ligações de um recibo, antes de as substituir num PUT. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from InvoiceReceipt ir where ir.receipt.id = :receiptId")
    void deleteByReceiptId(@Param("receiptId") UUID receiptId);
}
