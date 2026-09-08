package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionInvoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
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
     * @param outstanding    true = só as por liquidar (pago &lt; total, ou ainda sem total),
     *                       false = só as pagas por inteiro, null = todas. O pago é a soma das
     *                       ligações em {@code invoice_payment} — estado derivado, não coluna.
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
              and (:outstanding is null
                   or (i.relatedInvoiceId is null and (
                        (:outstanding = true  and (i.totalAmount is null
                             or coalesce((select sum(ip.amount) from InvoicePayment ip where ip.invoice = i), 0)
                                + coalesce((select sum(cn.totalAmount) from ConstructionInvoice cn where cn.relatedInvoiceId = i.id), 0)
                                < i.totalAmount))
                     or (:outstanding = false and i.totalAmount is not null
                             and coalesce((select sum(ip.amount) from InvoicePayment ip where ip.invoice = i), 0)
                                + coalesce((select sum(cn.totalAmount) from ConstructionInvoice cn where cn.relatedInvoiceId = i.id), 0)
                                >= i.totalAmount)
                   )))
              and (:sentToAccountant is null or i.sentToAccountant = :sentToAccountant)
              and (:from is null or i.invoiceDate >= :from)
              and (:to   is null or i.invoiceDate <= :to)
              and (:q is null
                   or lower(i.supplierName)     like lower(concat('%', :q, '%'))
                   or lower(i.supplierNif)      like lower(concat('%', :q, '%'))
                   or lower(i.invoiceNumber)    like lower(concat('%', :q, '%'))
                   or lower(i.invoiceAtcud)     like lower(concat('%', :q, '%'))
                   or exists (select 1 from ConstructionInvoiceDocument d
                              where d.invoice = i
                                and lower(d.originalFilename) like lower(concat('%', :q, '%')))
                   or lower(i.notes)            like lower(concat('%', :q, '%')))
            """)
    Page<ConstructionInvoice> search(@Param("enterpriseId") UUID enterpriseId,
                                     @Param("allocated") Boolean allocated,
                                     @Param("needsReview") Boolean needsReview,
                                     @Param("outstanding") Boolean outstanding,
                                     @Param("sentToAccountant") Boolean sentToAccountant,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("q") String q,
                                     Pageable pageable);

    /**
     * As faturas que não são de obra nenhuma: a quarentena ("Por identificar")
     * e as despesas da empresa. Sem filtro de obra, porque nestes dois âmbitos
     * o enterprise_id é nulo por construção (check da V26).
     */
    @Query("""
            select i from ConstructionInvoice i
            where i.scope = :scope
              and (:outstanding is null
                   or (i.relatedInvoiceId is null and (
                        (:outstanding = true  and (i.totalAmount is null
                             or coalesce((select sum(ip.amount) from InvoicePayment ip where ip.invoice = i), 0)
                                + coalesce((select sum(cn.totalAmount) from ConstructionInvoice cn where cn.relatedInvoiceId = i.id), 0)
                                < i.totalAmount))
                     or (:outstanding = false and i.totalAmount is not null
                             and coalesce((select sum(ip.amount) from InvoicePayment ip where ip.invoice = i), 0)
                                + coalesce((select sum(cn.totalAmount) from ConstructionInvoice cn where cn.relatedInvoiceId = i.id), 0)
                                >= i.totalAmount)
                   )))
              and (:q is null
                   or lower(i.supplierName)  like lower(concat('%', :q, '%'))
                   or lower(i.supplierNif)   like lower(concat('%', :q, '%'))
                   or lower(i.invoiceNumber) like lower(concat('%', :q, '%'))
                   or lower(i.invoiceAtcud)  like lower(concat('%', :q, '%'))
                   or lower(i.description)   like lower(concat('%', :q, '%'))
                   or lower(i.notes)         like lower(concat('%', :q, '%')))
            """)
    Page<ConstructionInvoice> searchByScope(@Param("scope") ConstructionInvoice.Scope scope,
                                            @Param("outstanding") Boolean outstanding,
                                            @Param("q") String q,
                                            Pageable pageable);

    /** Quantas faturas do projeto ainda estão por associar — alimenta o contador no orçamento. */
    @Query("""
            select count(i) from ConstructionInvoice i
            where i.enterprise.id = :enterpriseId
              and i.relatedInvoiceId is null
              and not exists (select 1 from ConstructionExpense e where e.invoice = i)
            """)
    long countPending(@Param("enterpriseId") UUID enterpriseId);

    // ── notas de crédito (fase 3) ─────────────────────────────
    // Uma linha com `related_invoice_id` preenchido É uma nota de crédito
    // (garantido pelo check `ck_invoice_credit_note_target` da V28).

    /** As notas de crédito de uma fatura, da mais recente para a mais antiga. */
    @Query("""
            select i from ConstructionInvoice i
            where i.relatedInvoiceId = :invoiceId
            order by i.invoiceDate desc nulls last, i.createdAt desc
            """)
    List<ConstructionInvoice> findCreditNotesFor(@Param("invoiceId") UUID invoiceId);

    /** As notas de crédito de uma página inteira de faturas, numa query. */
    @Query("select i from ConstructionInvoice i where i.relatedInvoiceId in :invoiceIds")
    List<ConstructionInvoice> findCreditNotesForAll(@Param("invoiceIds") Collection<UUID> invoiceIds);

    /** Σ do valor das notas de crédito de uma fatura (0 se não tiver). */
    @Query("select coalesce(sum(i.totalAmount), 0) from ConstructionInvoice i where i.relatedInvoiceId = :invoiceId")
    java.math.BigDecimal sumCreditNotesFor(@Param("invoiceId") UUID invoiceId);

    /**
     * Faturas com o mesmo ATCUD, em qualquer obra ou na quarentena (V29). Serve o aviso de duplicado: agora que
     * uma fatura vale por um único documento, um ATCUD repetido é quase sempre o
     * mesmo papel carregado duas vezes. Continua a avisar e não a bloquear —
     * quem carrega é que sabe se é engano.
     */
    @Query("""
            select i from ConstructionInvoice i
            where i.invoiceAtcud = :atcud
              and (:excludeId is null or i.id <> :excludeId)
            order by i.createdAt desc
            """)
    List<ConstructionInvoice> findByAtcud(@Param("atcud") String atcud,
                                          @Param("excludeId") UUID excludeId);


    /**
     * Faturas do projeto do mesmo fornecedor, para o serviço comparar o número
     * do documento já normalizado — ver
     * {@code ConstructionInvoiceService#normalizeDocumentNumber}.
     *
     * O ATCUD é a chave fiscal, mas quem completa à mão uma fatura sem QR
     * legível escreve o NIF e o número — o ATCUD raramente. Sem esta segunda
     * via, a verificação de duplicado só funcionava justamente nos casos em que
     * já não era precisa. O par (NIF, número) é a chave de negócio de uma
     * fatura portuguesa: o mesmo fornecedor não emite dois documentos com o
     * mesmo número — mas cada software escreve esse número de forma diferente
     * (espaços, hífens, barras), por isso a comparação não pode ser feita aqui
     * com um {@code =} exato: filtra-se só por fornecedor, e é o serviço que
     * normaliza e compara.
     */
    @Query("""
            select i from ConstructionInvoice i
            where i.supplierNif = :supplierNif
              and i.invoiceNumber is not null
              and (:excludeId is null or i.id <> :excludeId)
            order by i.createdAt desc
            """)
    List<ConstructionInvoice> findBySupplierNif(@Param("supplierNif") String supplierNif,
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

    interface SupplierRubricUse {
        UUID getBudgetItemId();
        long getUses();
        long getTotalUses();
    }

    /**
     * O mesmo que {@link #findBudgetItemIdsUsedBySupplier}, mas a trazer as
     * contagens: são elas que permitem à sugestão dizer <b>porquê</b> — "4 das 5
     * faturas deste fornecedor nesta obra foram para aqui" —, que é a diferença
     * entre um palpite e uma sugestão em que se confia.
     */
    @Query("""
            select e.budgetItem.id as budgetItemId, count(e) as uses,
                   (select count(e2) from ConstructionExpense e2
                     where e2.invoice.enterprise.id = :enterpriseId
                       and e2.invoice.supplierNif = :supplierNif) as totalUses
            from ConstructionExpense e
            where e.invoice.enterprise.id = :enterpriseId
              and e.invoice.supplierNif = :supplierNif
            group by e.budgetItem.id
            order by count(e) desc, max(e.createdAt) desc
            """)
    List<SupplierRubricUse> findRubricUsesBySupplier(@Param("enterpriseId") UUID enterpriseId,
                                                     @Param("supplierNif") String supplierNif,
                                                     Pageable pageable);

    interface SupplierRubricCodeUse {
        String getCode();
        long getUses();
        String getEnterpriseName();
    }

    /**
     * As rubricas onde este fornecedor foi lançado <b>noutras obras</b>, por
     * código.
     *
     * O código (`4.2.1`) é o que atravessa orçamentos: dois orçamentos do mesmo
     * empreiteiro repetem a numeração, e é isso que permite sugerir alguma coisa
     * na primeira fatura de um fornecedor numa obra nova. Quando os orçamentos
     * são de empreiteiros diferentes o código não bate certo e a sugestão
     * simplesmente não aparece — é a UI que diz de onde veio, e quem decide é
     * quem está a classificar.
     */
    @Query("""
            select e.budgetItem.code as code, count(e) as uses,
                   max(e.invoice.enterprise.name) as enterpriseName
            from ConstructionExpense e
            where e.invoice.supplierNif = :supplierNif
              and e.invoice.enterprise.id <> :enterpriseId
              and e.budgetItem.code is not null
            group by e.budgetItem.code
            order by count(e) desc, max(e.createdAt) desc
            """)
    List<SupplierRubricCodeUse> findRubricCodesUsedElsewhere(@Param("enterpriseId") UUID enterpriseId,
                                                             @Param("supplierNif") String supplierNif,
                                                             Pageable pageable);

    // ── catálogo de fornecedores (ver Supplier) ───────────────

    /** Um NIF que aparece nas faturas e ainda não tem empresa associada. */
    interface UnknownSupplierNif {
        String getNif();
        long getInvoiceCount();
        /**
         * Um nome que alguém já escreveu à mão nalguma fatura deste NIF, se
         * existir — poupa a escrita quando o trabalho já foi feito uma vez.
         */
        String getSuggestedName();
        LocalDate getLastInvoiceDate();
    }

    /**
     * Os NIFs vistos nas faturas de todos os projetos que ainda não estão no
     * catálogo, do mais frequente para o menos — é esta a lista de trabalho de
     * quem vai dar nome às empresas.
     */
    @Query("""
            select i.supplierNif      as nif,
                   count(i)           as invoiceCount,
                   max(i.supplierName) as suggestedName,
                   max(i.invoiceDate)  as lastInvoiceDate
            from ConstructionInvoice i
            where i.supplierNif is not null
              and not exists (select 1 from Supplier s where s.nif = i.supplierNif)
            group by i.supplierNif
            order by count(i) desc, i.supplierNif
            """)
    List<UnknownSupplierNif> findUnknownSupplierNifs();

    interface SupplierNifCount {
        String getNif();
        long getInvoiceCount();
    }

    /** Quantas faturas tem cada NIF do catálogo — numa query, não uma por linha. */
    @Query("""
            select i.supplierNif as nif, count(i) as invoiceCount
            from ConstructionInvoice i
            where i.supplierNif in :nifs
            group by i.supplierNif
            """)
    List<SupplierNifCount> countBySupplierNifs(@Param("nifs") Collection<String> nifs);

    /**
     * Escreve o nome da empresa nas faturas deste NIF que estejam sem nome.
     *
     * Só nas vazias: um nome escrito à mão é uma correção deliberada de alguém
     * que tinha o papel à frente, e o catálogo não tem autoridade para a deitar
     * fora.
     *
     * @return quantas faturas foram atualizadas
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ConstructionInvoice i set i.supplierName = :name
            where i.supplierNif = :nif
              and (i.supplierName is null or trim(i.supplierName) = '')
            """)
    int fillMissingSupplierName(@Param("nif") String nif, @Param("name") String name);
}
