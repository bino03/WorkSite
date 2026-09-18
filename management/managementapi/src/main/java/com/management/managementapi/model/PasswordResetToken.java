package com.management.managementapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Um pedido de recuperação de password (`V22`).
 *
 * Espelha {@link PendingInvite}: token opaco, prazo e marca de uso. `usedAt` nulo
 * significa por usar — um token só serve uma vez, e o prazo é curto (1 hora)
 * porque quem o tem por mãos entra na conta sem saber a password antiga.
 */
@Entity
@Table(name = "password_reset_tokens", schema = "settings")
@Getter
@Setter
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "auth_user_id", nullable = false)
    private UUID authUserId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    // Preenchidos pela base de dados (DEFAULT NOW() da V22): com insertable a true
    // o Hibernate mandava null explícito e o INSERT falhava na NOT NULL — o mesmo
    // buraco do EmailProvider, apanhado no browser a 2026-09-18.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
