package com.management.managementapi.repository.attendance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.TimeEntry;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    Optional<TimeEntry> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * As picagens de uma pessoa num intervalo — a consulta de base de todo o módulo.
     * O intervalo é meio-aberto ({@code >= from}, {@code < to}): um dia vai de 00:00
     * ao 00:00 seguinte, e um instante não pode cair em dois dias.
     */
    @Query("""
            select e from TimeEntry e
            left join fetch e.enterprise
            where e.profile.id = :profileId
              and e.deletedAt is null
              and e.happenedAt >= :from
              and e.happenedAt < :to
            order by e.happenedAt asc
            """)
    List<TimeEntry> findForProfileBetween(@Param("profileId") UUID profileId,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to);

    @Query("""
            select e from TimeEntry e
            left join fetch e.enterprise
            where e.deletedAt is null
              and e.happenedAt >= :from
              and e.happenedAt < :to
              and (:enterpriseId is null or e.enterprise.id = :enterpriseId)
            order by e.happenedAt asc
            """)
    List<TimeEntry> findBetween(@Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                @Param("enterpriseId") UUID enterpriseId);

    Page<TimeEntry> findByProfileIdAndDeletedAtIsNullOrderByHappenedAtDesc(UUID profileId, Pageable pageable);

    List<TimeEntry> findByProfileIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID profileId);

    /**
     * Se existe uma picagem (viva ou apagada) deste funcionário. É o que impede
     * eliminar quem tenha registo de assiduidade — a lei obriga a guardá-lo, e um
     * soft-delete não o faz desaparecer.
     */
    boolean existsByProfileId(UUID profileId);

    /**
     * Todas as picagens do período, <b>incluindo as anuladas</b> — é a vista de
     * auditoria. Um ficheiro que só mostrasse as vivas esconderia precisamente as
     * picagens que alguém decidiu tirar.
     */
    @Query("""
            select e from TimeEntry e
            join fetch e.profile
            left join fetch e.enterprise
            left join fetch e.registeredBy
            where e.happenedAt >= :from
              and e.happenedAt < :to
            order by e.profile.name asc, e.happenedAt asc
            """)
    List<TimeEntry> findAllForAudit(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);
}
