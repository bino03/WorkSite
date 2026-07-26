package com.management.managementapi.model;

import com.management.managementapi.model.enums.ProfileRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_invites" , schema="settings")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class PendingInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;


    private UUID created_by;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileRole role;

    @Column(name = "invite_token", nullable = false, unique = true, length = 500)
    private String inviteToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InviteStatus status = InviteStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy; // Profile ID de quem enviou

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum InviteStatus {
        PENDING,
        ACCEPTED,
        EXPIRED,
        CANCELLED
    }
}