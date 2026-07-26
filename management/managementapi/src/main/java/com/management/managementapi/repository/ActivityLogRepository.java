package com.management.managementapi.repository;

import com.management.managementapi.model.ActivityLog;
import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    // Atividades de um utilizador, mais recentes primeiro
    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Todas as atividades, mais recentes primeiro
    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Histórico completo de uma entidade específica
    List<ActivityLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            EntityType entityType, UUID entityId);

    // Filtro combinado: userId, activityType, entityType, range de datas
    @Query("""
        SELECT a FROM ActivityLog a
        WHERE (:userId       IS NULL OR a.userId       = :userId)
          AND (:activityType IS NULL OR a.activityType = :activityType)
          AND (:entityType   IS NULL OR a.entityType   = :entityType)
          AND (:dateFrom     IS NULL OR a.createdAt   >= :dateFrom)
          AND (:dateTo       IS NULL OR a.createdAt   <= :dateTo)
        ORDER BY a.createdAt DESC
        """)
    Page<ActivityLog> findWithFilters(
            @Param("userId")       UUID userId,
            @Param("activityType") ActivityType activityType,
            @Param("entityType")   EntityType entityType,
            @Param("dateFrom")     OffsetDateTime dateFrom,
            @Param("dateTo")       OffsetDateTime dateTo,
            Pageable pageable);
}
