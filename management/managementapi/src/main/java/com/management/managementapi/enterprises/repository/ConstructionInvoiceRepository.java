package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionInvoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConstructionInvoiceRepository extends JpaRepository<ConstructionInvoice, UUID> {

    /**
     * A caixa de entrada, com os filtros todos opcionais.
     *
     * "Por associar" ({@code allocated = false}) é a vista por omissão e a razão
     * de ser desta tabela: a lista do que entrou e ainda não foi classificado.
     *
     * @param allocated      true = só as já ligadas a uma rubrica, false = só as pendentes, null = todas
     * @param needsReview    true = só as que ficaram sem data ou sem total (QR ilegível)
     */
    @Query("""
            select i from ConstructionInvoice i
            where i.enterprise.id = :enterpriseId
              and (:allocated is null
                   or (:allocated = true  and     exists (select 1 from ConstructionExpense e where e.invoice = i))
                   or (:allocated = false and not exists (select 1 from ConstructionExpense e where e.invoice = i)))
              and (:needsReview is null
                   or (:needsReview = true  and (i.invoiceDate is null or i.totalAmount is null))
                   or (:needsReview = false and  i.invoiceDate is not null and i.totalAmount is not null))
              and (:sentToAccountant is null or i.sentToAccountant = :sentToAccountant)
              and (:from is null or i.invoiceDate >= :from)
              and (:to   is null or i.invoiceDate <= :to)
              and (:q is null
                   or lower(i.supplierName)     like lower(concat('%', :q, '%'))
                   or lower(i.supplierNif)      like lower(concat('%', :q, '%'))
                   or lower(i.invoiceNumber)    like lower(concat('%', :q, '%'))
                   or lower(i.invoiceAtcud)     like lower(concat('%', :q, '%'))
                   or lower(i.originalFilename) like lower(concat('%', :q, '%'))
                   or lower(i.notes)            like lower(concat('%', :q, '%')))
            """)
    Page<ConstructionInvoice> search(@Param("enterpriseId") UUID enterpriseId,
                                     @Param("allocated") Boolean allocated,
                                     @Param("needsReview") Boolean needsReview,
                                     @Param("sentToAccountant") Boolean sentToAccountant,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("q") String q,
                                     Pageable pageable);

    /** Quantas faturas do projeto ainda estão por associar — alimenta o contador no orçamento. */
    @Query("""
            select count(i) from ConstructionInvoice i
            where i.enterprise.id = :enterpriseId
              and not exists (select 1 from ConstructionExpense e where e.invoice = i)
            """)
    long countPending(@Param("enterpriseId") UUID enterpriseId);

    /**
     * Faturas do projeto com o mesmo ATCUD. Serve o aviso de duplicado: agora que
     * uma fatura vale por um único documento, um ATCUD repetido é quase sempre o
     * mesmo papel carregado duas vezes. Continua a avisar e não a bloquear —
     * quem carrega é que sabe se é engano.
     */
    @Query("""
            select i from ConstructionInvoice i
            where i.enterprise.id = :enterpriseId
              and i.invoiceAtcud = :atcud
              and (:excludeId is null or i.id <> :excludeId)
            order by i.uploadedAt desc
            """)
    List<ConstructionInvoice> findByEnterpriseAndAtcud(@Param("enterpriseId") UUID enterpriseId,
                                                       @Param("atcud") String atcud,
                                                       @Param("excludeId") UUID excludeId);

    /**
     * Faturas do projeto do mesmo fornecedor com o mesmo número de documento.
     *
     * O ATCUD é a chave fiscal, mas quem completa à mão uma fatura sem QR
     * legível escreve o NIF e o número — o ATCUD raramente. Sem esta segunda
     * via, a verificação de duplicado só funcionava justamente nos casos em que
     * já não era precisa. O par (NIF, número) é a chave de negócio de uma
     * fatura portuguesa: o mesmo fornecedor não emite dois documentos com o
     * mesmo número.
     */
    @Query("""
            select i from ConstructionInvoice i
            where i.enterprise.id = :enterpriseId
              and i.supplierNif = :supplierNif
              and i.invoiceNumber = :invoiceNumber
              and (:excludeId is null or i.id <> :excludeId)
            order by i.uploadedAt desc
            """)
    List<ConstructionInvoice> findByEnterpriseAndSupplierDocument(@Param("enterpriseId") UUID enterpriseId,
                                                                  @Param("supplierNif") String supplierNif,
                                                                  @Param("invoiceNumber") String invoiceNumber,
                                                                  @Param("excludeId") UUID excludeId);

    /**
     * Rubricas onde as faturas deste fornecedor já foram lançadas, da mais usada
     * para a menos usada. É o que permite sugerir a rubrica no momento de
     * associar — na segunda fatura do mesmo fornecedor a escolha passa a um clique.
     */
    @Query("""
            select e.budgetItem.id from ConstructionExpense e
            where e.invoice.enterprise.id = :enterpriseId
              and e.invoice.supplierNif = :supplierNif
            group by e.budgetItem.id
            order by count(e) desc, max(e.createdAt) desc
            """)
    List<UUID> findBudgetItemIdsUsedBySupplier(@Param("enterpriseId") UUID enterpriseId,
                                               @Param("supplierNif") String supplierNif,
                                               Pageable pageable);
}
