package com.management.managementapi.enterprises.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.InvoicePayment;
import com.management.managementapi.enterprises.model.InvoicePaymentId;

@Repository
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, InvoicePaymentId> {

    /** Ligações de uma fatura, para montar o resumo de pagamentos no detalhe. */
    List<InvoicePayment> findByInvoiceId(UUID invoiceId);

    /** Ligações de um pagamento — usadas ao anular, para repor as faturas. */
    List<InvoicePayment> findByPaymentId(UUID paymentId);

    /** Todas as ligações de um conjunto de pagamentos — para saber "junto com" que faturas. */
    List<InvoicePayment> findByPaymentIdIn(Collection<UUID> paymentIds);

    /** Ligações de uma página inteira de faturas numa query, em vez de uma por linha. */
    List<InvoicePayment> findByInvoiceIdIn(Collection<UUID> invoiceIds);

    /** Quanto já foi pago de uma fatura (0 se nenhuma ligação). */
    @Query("""
            select coalesce(sum(ip.amount), 0)
            from InvoicePayment ip
            where ip.invoice.id = :invoiceId
            """)
    BigDecimal sumPaidByInvoice(@Param("invoiceId") UUID invoiceId);

    interface InvoicePaidSum {
        UUID getInvoiceId();
        BigDecimal getPaid();
    }

    /** Soma paga por várias faturas de uma vez — alimenta o selo de estado nas listas. */
    @Query("""
            select ip.invoice.id as invoiceId, coalesce(sum(ip.amount), 0) as paid
            from InvoicePayment ip
            where ip.invoice.id in :invoiceIds
            group by ip.invoice.id
            """)
    List<InvoicePaidSum> sumPaidByInvoices(@Param("invoiceIds") Collection<UUID> invoiceIds);
}
