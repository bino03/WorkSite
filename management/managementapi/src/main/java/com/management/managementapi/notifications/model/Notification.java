package com.management.managementapi.notifications.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Aviso in-app dirigido a um profile.
 *
 * O texto é guardado já escrito em vez de tipo + parâmetros: é o que torna a
 * leitura trivial (a lista é um `select`, sem resolver entidades que podem
 * entretanto ter desaparecido). O custo é o histórico ficar na língua em que
 * nasceu — assumido para a v1, ver `V20__notification.sql`.
 */
@Entity
@Table(name = "notification", schema = "worksite")
public class Notification extends BaseEntity {

    /** Tarefa atribuída ao destinatário. */
    public static final String TYPE_TASK_ASSIGNED = "task_assigned";

    /** Fatura carregada e ainda por classificar numa rubrica. */
    public static final String TYPE_INVOICE_PENDING = "invoice_pending";

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "link")
    private String link;

    /** Entidade de origem. Sem FK: aponta para tabelas diferentes conforme o tipo. */
    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    public UUID getRecipientId() { return recipientId; }
    public void setRecipientId(UUID recipientId) { this.recipientId = recipientId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
}
