package com.management.managementapi.repository;

import com.management.managementapi.model.PasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Queima os pedidos anteriores do mesmo utilizador. Corre a cada pedido novo:
     * sem isto, pedir duas vezes deixava dois links válidos em circulação, e o mais
     * antigo — que pode ter ficado num email reencaminhado — continuava a servir.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PasswordResetToken t
           set t.usedAt = :now
         where t.authUserId = :authUserId
           and t.usedAt is null
    """)
    int invalidatePending(@Param("authUserId") UUID authUserId, @Param("now") Instant now);
}
