package com.management.managementapi.notifications.service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.notifications.dto.NotificationResponseDTO;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Notificações in-app.
 *
 * A escrita ({@link #notify}) é chamada de dentro dos serviços de domínio, na
 * mesma transação do que a originou: se a tarefa não for gravada, a
 * notificação também não existe. O inverso — notificar primeiro e gravar
 * depois — deixaria avisos a apontar para coisas que nunca chegaram a existir.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    // ── escrita (uso interno, a partir dos serviços de domínio) ──

    /**
     * Cria uma notificação. {@code recipientId} nulo é ignorado em silêncio —
     * acontece quando o destinatário é derivado de algo opcional, e não vale um
     * erro no meio de uma operação de negócio que correu bem.
     */
    public void notify(UUID recipientId, String type, String title, String body, String link, UUID entityId) {
        if (recipientId == null) return;

        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setEntityId(entityId);
        repository.save(n);

        log.debug("[notificacao] {} para {} (entidade {})", type, recipientId, entityId);
    }

    /** Igual ao {@link #notify}, para vários destinatários. */
    public void notifyAll(Collection<UUID> recipientIds, String type, String title, String body,
                          String link, UUID entityId) {
        if (recipientIds == null) return;
        recipientIds.forEach(id -> notify(id, type, title, body, link, entityId));
    }

    // ── leitura ──

    @Transactional(readOnly = true)
    public Page<NotificationResponseDTO> list(UUID recipientId, Pageable pageable) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    /**
     * Marca uma como lida. Só o destinatário o pode fazer — sem esta verificação
     * o id no URL bastaria para mexer nas notificações de outra pessoa.
     */
    public NotificationResponseDTO markRead(UUID id, UUID recipientId) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!n.getRecipientId().equals(recipientId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        if (n.getReadAt() == null) {
            n.setReadAt(OffsetDateTime.now());
            repository.save(n);
        }
        return toDTO(n);
    }

    /** Devolve quantas passaram a lidas. */
    public int markAllRead(UUID recipientId) {
        return repository.markAllRead(recipientId, OffsetDateTime.now());
    }

    private NotificationResponseDTO toDTO(Notification n) {
        return new NotificationResponseDTO(
                n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getLink(), n.getEntityId(), n.getReadAt(), n.getCreatedAt());
    }
}
