package com.management.managementapi.repository.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.enums.AbsenceStatus;

@Repository
public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

    Optional<Absence> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * As ausências de uma pessoa que tocam um intervalo. A condição é de
     * sobreposição ({@code startsOn <= to and endsOn >= from}), não de contenção:
     * umas férias que começam em dezembro e acabam em janeiro contam nos dois meses.
     *
     * <p>O estado vai como {@code @Param}, nunca como literal — ver a armadilha dos
     * enums NAMED_ENUM em {@code ConstructionBudgetItemRepository}.
     */
    @Query("""
            select a from Absence a
            where a.profile.id = :profileId
              and a.deletedAt is null
              and a.startsOn <= :to
              and a.endsOn >= :from
              and (:status is null or a.status = :status)
            order by a.startsOn asc
            """)
    List<Absence> findForProfileOverlapping(@Param("profileId") UUID profileId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("status") AbsenceStatus status);

    /** O mapa de equipa: todos os que estão fora num intervalo. */
    @Query("""
            select a from Absence a
            join fetch a.profile
            where a.deletedAt is null
              and a.startsOn <= :to
              and a.endsOn >= :from
              and (:status is null or a.status = :status)
            order by a.startsOn asc
            """)
    List<Absence> findOverlapping(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("status") AbsenceStatus status);

    @Query("""
            select a from Absence a
            join fetch a.profile
            where a.deletedAt is null
              and a.status = :status
            order by a.startsOn asc
            """)
    List<Absence> findByStatus(@Param("status") AbsenceStatus status);

    List<Absence> findByProfileIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID profileId);
}
