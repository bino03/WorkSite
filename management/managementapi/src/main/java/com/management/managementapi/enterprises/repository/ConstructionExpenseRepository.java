package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionExpense;

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
public interface ConstructionExpenseRepository extends JpaRepository<ConstructionExpense, UUID> {

    List<ConstructionExpense> findByBudgetItemIdOrderByCreatedAtDesc(UUID budgetItemId);

    /**
     * Todas as despesas de um projeto numa só query — alimenta os rollups da
     * árvore sem cair no N+1 que existia antes (uma chamada por sub-etapa).
     */
    @Query("""
            select e from ConstructionExpense e
            where e.budgetItem.enterprise.id = :enterpriseId
            """)
    List<ConstructionExpense> findAllByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    /**
     * Despesas do projeto lançadas a partir da mesma fatura (mesmo ATCUD).
     * Alimenta o aviso de duplicado — não bloqueia, porque repartir uma fatura
     * por várias rubricas da obra é prática normal.
     */
    @Query("""
            select e from ConstructionExpense e
            where e.budgetItem.enterprise.id = :enterpriseId
              and e.invoiceAtcud = :atcud
            order by e.expenseDate desc
            """)
    List<ConstructionExpense> findByEnterpriseAndAtcud(@Param("enterpriseId") UUID enterpriseId,
                                                       @Param("atcud") String atcud);

    /**
     * Lista plana das despesas do projeto, com filtros opcionais — é a vista de
     * trabalho de quem trata da contabilidade. Sem isto, responder a "o que
     * falta enviar?" obrigava a percorrer a árvore rubrica a rubrica.
     *
     * @param hasInvoice true = só com fatura anexada, false = só sem, null = todas
     */
    @Query("""
            select e from ConstructionExpense e
            where e.budgetItem.enterprise.id = :enterpriseId
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
              and (:sentToAccountant is null or e.sentToAccountant = :sentToAccountant)
              and (:hasInvoice is null
                   or (:hasInvoice = true  and e.storageKey is not null)
                   or (:hasInvoice = false and e.storageKey is null))
              and (:q is null
                   or lower(e.name) like lower(concat('%', :q, '%'))
                   or lower(e.description) like lower(concat('%', :q, '%'))
                   or lower(e.budgetItem.code) like lower(concat('%', :q, '%'))
                   or lower(e.budgetItem.name) like lower(concat('%', :q, '%')))
            """)
    Page<ConstructionExpense> search(@Param("enterpriseId") UUID enterpriseId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("sentToAccountant") Boolean sentToAccountant,
                                     @Param("hasInvoice") Boolean hasInvoice,
                                     @Param("q") String q,
                                     Pageable pageable);
}
