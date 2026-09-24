package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionBudget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConstructionBudgetRepository extends JpaRepository<ConstructionBudget, UUID> {

    List<ConstructionBudget> findByEnterpriseIdOrderBySortOrderAscCreatedAtAsc(UUID enterpriseId);

    /** Os lotes vivos de várias obras de uma vez — para listagens que precisam só do nome. */
    List<ConstructionBudget> findByEnterpriseIdInAndDeletedAtIsNullOrderBySortOrderAsc(Collection<UUID> enterpriseIds);

    boolean existsByEnterpriseIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID enterpriseId, String name);

    @Query("""
            select coalesce(max(b.sortOrder), -1) + 1 from ConstructionBudget b
            where b.enterprise.id = :enterpriseId
            """)
    int nextSortOrder(@Param("enterpriseId") UUID enterpriseId);
}
