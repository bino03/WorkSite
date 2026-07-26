package com.management.managementapi.repository;

import com.management.managementapi.model.PendingInvite;
import com.management.managementapi.model.enums.ProfileRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PendingInviteRepository extends JpaRepository<PendingInvite, UUID> {
    
    Optional<PendingInvite> findByInviteToken(String inviteToken);
    
    boolean existsByEmailAndStatus(String email, PendingInvite.InviteStatus status);

       // ✅ NOVO: Buscar todos com filtros opcionais
    @Query("""
        SELECT pi FROM PendingInvite pi
        WHERE (:status IS NULL OR pi.status = :status)
        AND (:role IS NULL OR pi.role = :role)
        ORDER BY pi.sentAt DESC
    """)
    List<PendingInvite> findAllWithFilters(
        @Param("status") PendingInvite.InviteStatus status,
        @Param("role") ProfileRole role
    );
}
