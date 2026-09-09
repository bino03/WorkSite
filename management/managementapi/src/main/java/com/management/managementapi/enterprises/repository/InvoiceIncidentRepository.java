package com.management.managementapi.enterprises.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.InvoiceIncident;

@Repository
public interface InvoiceIncidentRepository extends JpaRepository<InvoiceIncident, UUID> {

    /** Por resolver primeiro, depois os mais recentes. */
    @Query("select i from InvoiceIncident i order by "
         + "case when i.resolvedAt is null then 0 else 1 end, i.createdAt desc")
    List<InvoiceIncident> findAllOrdered();

    List<InvoiceIncident> findByInvoicesId(UUID invoiceId);
}
