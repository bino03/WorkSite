package com.management.managementapi.notifications.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.notifications.model.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    /**
     * Já existe este aviso para este destinatário e esta entidade? É a única
     * barreira contra repetições nos avisos gerados por jobs agendados — a
     * tabela não tem unique index nisto de propósito (os gatilhos manuais
     * podem legitimamente repetir um tipo para a mesma entidade).
     */
    boolean existsByRecipientIdAndTypeAndEntityId(UUID recipientId, String type, UUID entityId);

    /**
     * Marca todas as por ler do destinatário. Em bloco e não uma a uma: o
     * "marcar todas como lidas" é o caso normal quando o sino tem 20 avisos.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.readAt = :now
             where n.recipientId = :recipientId
               and n.readAt is null
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") OffsetDateTime now);
}
