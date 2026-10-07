package com.management.managementapi.repository.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.Employment;

@Repository
public interface EmploymentRepository extends JpaRepository<Employment, UUID> {

    @EntityGraph(attributePaths = { "terms", "terms.workSchedule" })
    Optional<Employment> findByProfileId(UUID profileId);

    @EntityGraph(attributePaths = { "terms", "terms.workSchedule" })
    Optional<Employment> findWithTermsById(UUID id);

    @EntityGraph(attributePaths = { "terms", "terms.workSchedule", "profile" })
    List<Employment> findAllByOrderByHiredAtDesc();

    boolean existsByProfileId(UUID profileId);

    /**
     * Quem esteve vinculado em algum dia do período — inclui quem saiu a meio: o
     * mês em que alguém sai também tem de fechar com as horas dessa pessoa.
     */
    @EntityGraph(attributePaths = { "profile" })
    @Query("""
            select e from Employment e
            where e.hiredAt <= :to
              and (e.endedAt is null or e.endedAt >= :from)
            order by e.profile.name asc
            """)
    List<Employment> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
