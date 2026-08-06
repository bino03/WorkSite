package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionBudgetItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConstructionBudgetItemRepository extends JpaRepository<ConstructionBudgetItem, UUID> {

    /**
     * Carrega a árvore inteira de um projeto numa só query — a montagem
     * pai/filho é feita em memória. Um orçamento típico ronda as 200 linhas,
     * por isso isto sai muito mais barato do que descer nível a nível.
     */
    @Query("""
            select i from ConstructionBudgetItem i
            where i.enterprise.id = :enterpriseId
            order by i.sortOrder asc
            """)
    List<ConstructionBudgetItem> findTreeByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    List<ConstructionBudgetItem> findByParentIdOrderBySortOrderAsc(UUID parentId);

    Optional<ConstructionBudgetItem> findByEnterpriseIdAndCode(UUID enterpriseId, String code);

    boolean existsByParentId(UUID parentId);

    boolean existsByEnterpriseId(UUID enterpriseId);

    /**
     * Apaga o orçamento inteiro de um projeto numa só instrução — a FK
     * auto-referenciada tem {@code on delete cascade}, por isso o Postgres
     * trata da sub-árvore (e das despesas) sozinho.
     */
    @Modifying
    @Query("delete from ConstructionBudgetItem i where i.enterprise.id = :enterpriseId")
    void deleteAllByEnterpriseId(@Param("enterpriseId") UUID enterpriseId);

    /** Próxima posição livre entre os irmãos — usada ao criar uma rubrica nova. */
    @Query("""
            select coalesce(max(i.sortOrder), -1) + 1 from ConstructionBudgetItem i
            where i.enterprise.id = :enterpriseId
              and ((:parentId is null and i.parent is null) or i.parent.id = :parentId)
            """)
    int nextSortOrder(@Param("enterpriseId") UUID enterpriseId, @Param("parentId") UUID parentId);
}
