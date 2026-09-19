package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConstructionBudgetItemRepository extends JpaRepository<ConstructionBudgetItem, UUID> {

    /**
     * Carrega a árvore inteira de um projeto numa só query — a montagem
     * pai/filho é feita em memória. Um orçamento típico ronda as 200 linhas,
     * por isso isto sai muito mais barato do que descer nível a nível.
     *
     * <b>Inclui as rubricas eliminadas</b> (soft delete, {@code deleted_at}) de
     * propósito: quem constrói a árvore visível filtra-as (uma vez, em memória);
     * quem precisa da sub-árvore de uma eliminação/recuperação precisa delas
     * todas. Ter as duas coisas na mesma query poupa uma segunda ida à BD.
     */
    @Query("""
            select i from ConstructionBudgetItem i
            where i.enterprise.id = :enterpriseId
            order by i.sortOrder asc
            """)
    List<ConstructionBudgetItem> findTreeByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    /**
     * Rubricas vivas de várias obras de uma vez — para a lista de projetos
     * mostrar o total do orçamento de cada uma sem ir à BD por obra.
     */
    @Query("""
            select i from ConstructionBudgetItem i
            where i.enterprise.id in :enterpriseIds
              and i.deletedAt is null
            """)
    List<ConstructionBudgetItem> findLiveByEnterpriseIdIn(@Param("enterpriseIds") Collection<UUID> enterpriseIds);

    /** Só os irmãos vivos — usada ao reordenar (uma rubrica eliminada não conta posição). */
    @Query("""
            select i from ConstructionBudgetItem i
            where i.parent.id = :parentId and i.deletedAt is null
            order by i.sortOrder asc
            """)
    List<ConstructionBudgetItem> findByParentIdOrderBySortOrderAsc(@Param("parentId") UUID parentId);

    /**
     * O código de uma rubrica eliminada fica livre para reutilização — por
     * isso o filtro por {@code deleted_at is null} aqui, e não só na árvore.
     */
    @Query("""
            select i from ConstructionBudgetItem i
            where i.enterprise.id = :enterpriseId and i.code = :code and i.deletedAt is null
            """)
    Optional<ConstructionBudgetItem> findByEnterpriseIdAndCode(@Param("enterpriseId") UUID enterpriseId,
                                                               @Param("code") String code);

    boolean existsByParentId(UUID parentId);

    /** Tem pelo menos uma filha deste tipo? Usado pelo filtro "ao capítulo" das faturas. */
    boolean existsByParentIdAndRowKind(UUID parentId, BudgetRowKind rowKind);

    /** Só conta as vivas — um projeto com tudo eliminado é tratado como sem orçamento (importação). */
    @Query("""
            select count(i) > 0 from ConstructionBudgetItem i
            where i.enterprise.id = :enterpriseId and i.deletedAt is null
            """)
    boolean existsByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    /** As rubricas eliminadas de um projeto — a zona de recuperação. */
    List<ConstructionBudgetItem> findByEnterpriseIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID enterpriseId);

    /**
     * Apaga o orçamento inteiro de um projeto numa só instrução — a FK
     * auto-referenciada tem {@code on delete cascade}, por isso o Postgres
     * trata da sub-árvore (e das despesas) sozinho. Hard delete de propósito
     * (não passa pelo soft delete): é a reimportação a substituir tudo, não
     * uma eliminação normal a acontecer no ecrã.
     */
    @Modifying
    @Query("delete from ConstructionBudgetItem i where i.enterprise.id = :enterpriseId")
    void deleteAllByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    /** Próxima posição livre entre os irmãos vivos — usada ao criar/mover uma rubrica. */
    @Query("""
            select coalesce(max(i.sortOrder), -1) + 1 from ConstructionBudgetItem i
            where i.enterprise.id = :enterpriseId
              and ((:parentId is null and i.parent is null) or i.parent.id = :parentId)
              and i.deletedAt is null
            """)
    int nextSortOrder(@Param("enterpriseId") UUID enterpriseId, @Param("parentId") UUID parentId);

    /**
     * Rubricas vivas cujo prazo termina na janela {@code [from, to]}, em obras
     * ainda em curso. Só {@code ITEM}: títulos e notas podem ter datas herdadas
     * do Excel, mas não são trabalho que se atrase. O {@code join fetch} traz
     * a obra porque o aviso escreve o nome dela.
     *
     * O {@code rowKind} vai como parâmetro e não como literal: um literal de enum
     * em JPQL sai como {@code 'ITEM'::BudgetRowKind}, e o tipo no Postgres
     * chama-se {@code worksite.budget_row_kind}.
     */
    @Query("""
            select i from ConstructionBudgetItem i
            join fetch i.enterprise e
            where i.rowKind = :rowKind
              and i.deletedAt is null
              and i.endDate between :from and :to
              and e.status not in (
                  com.management.managementapi.enterprises.model.enums.EnterPriseStatus.completed,
                  com.management.managementapi.enterprises.model.enums.EnterPriseStatus.archived,
                  com.management.managementapi.enterprises.model.enums.EnterPriseStatus.deleted)
            order by i.endDate asc
            """)
    List<ConstructionBudgetItem> findActiveItemsEndingBetween(@Param("rowKind") BudgetRowKind rowKind,
                                                              @Param("from") LocalDate from,
                                                              @Param("to") LocalDate to);

    /**
     * Purga real (hard delete) das rubricas eliminadas há mais de 30 dias — a
     * FK auto-referenciada com {@code on delete cascade} trata da sub-árvore.
     * Uma rubrica só chega a ser marcada eliminada sem despesas por baixo
     * (bloqueado no `delete()` do serviço), por isso não há despesas a perder
     * aqui.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            delete from worksite.construction_budget_item
            where deleted_at is not null and deleted_at < now() - interval '30 days'
            """, nativeQuery = true)
    int purgeDeletedOlderThan30Days();
}
