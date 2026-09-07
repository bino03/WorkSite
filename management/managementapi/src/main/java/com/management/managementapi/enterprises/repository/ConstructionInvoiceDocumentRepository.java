package com.management.managementapi.enterprises.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;

@Repository
public interface ConstructionInvoiceDocumentRepository extends JpaRepository<ConstructionInvoiceDocument, UUID> {

    /**
     * Os documentos de uma fatura pela ordem por que entraram. O primeiro é o
     * que a lista mostra e o que o {@code rescan} relê.
     */
    List<ConstructionInvoiceDocument> findByInvoiceIdOrderByUploadedAtAsc(UUID invoiceId);

    Optional<ConstructionInvoiceDocument> findFirstByInvoiceIdOrderByUploadedAtAsc(UUID invoiceId);

    /** Uma query para os documentos de uma página inteira de faturas, em vez de uma por linha. */
    List<ConstructionInvoiceDocument> findByInvoiceIdInOrderByUploadedAtAsc(Collection<UUID> invoiceIds);

    Optional<ConstructionInvoiceDocument> findByIdAndInvoiceId(UUID id, UUID invoiceId);

    long countByInvoiceId(UUID invoiceId);

    /**
     * O mesmo ficheiro, byte a byte, esteja ele onde estiver — obra, quarentena
     * ou despesas da empresa. Ao contrário do índice que a V18 tinha na fatura,
     * esta procura é <b>global</b>: é o que o vault da Vilatro já assume
     * (decisão 18) e o que o índice único da V24 impõe na base de dados.
     */
    @Query("""
            select d from ConstructionInvoiceDocument d
            where d.checksumSha256 = :checksum
              and (:excludeInvoiceId is null or d.invoice.id <> :excludeInvoiceId)
            order by d.uploadedAt desc
            """)
    List<ConstructionInvoiceDocument> findByChecksum(@Param("checksum") String checksum,
                                                     @Param("excludeInvoiceId") UUID excludeInvoiceId);
}
