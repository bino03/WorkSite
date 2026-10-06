package com.management.managementapi.model.attendance;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntryChange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * o rasto de uma alteração a uma {@link TimeEntry}, com o estado <em>anterior</em>.
 *
 * <p>Existe porque o {@code worksite.activity_log} é {@code @Async} e pode perder
 * linhas numa falha. Para faturas isso nunca importou; num registo legal de
 * assiduidade é precisamente o registo corrigido que uma auditoria põe em causa.
 * Esta tabela escreve-se na <b>mesma transação</b> da alteração.
 *
 * <p>Append-only: não estende {@code BaseEntity} porque não tem — nem pode ter —
 * {@code updated_at}. Uma revisão que se pudesse alterar não provava nada.
 *
 * <p>{@code timeEntryId} é um UUID solto e não uma FK mapeada: o rasto tem de
 * sobreviver à picagem, e {@code changedByName} fica guardado já escrito para o
 * nome ser o que a pessoa tinha na altura, como o {@code activity_log} faz.
 */
@Entity
@Table(name = "time_entry_revision", schema = "attendance")
public class TimeEntryRevision {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "time_entry_id", nullable = false)
    private UUID timeEntryId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "change", columnDefinition = "attendance.time_entry_change", nullable = false)
    private TimeEntryChange change;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_by_name", nullable = false)
    private String changedByName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "previous_happened_at")
    private OffsetDateTime previousHappenedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_direction", columnDefinition = "attendance.time_direction")
    private TimeDirection previousDirection;

    @Column(name = "previous_enterprise_id")
    private UUID previousEnterpriseId;

    @Column(name = "previous_note", columnDefinition = "TEXT")
    private String previousNote;

    @Column(name = "previous_deleted_at")
    private OffsetDateTime previousDeletedAt;

    public UUID getId() { return id; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public UUID getTimeEntryId() { return timeEntryId; }
    public void setTimeEntryId(UUID timeEntryId) { this.timeEntryId = timeEntryId; }

    public TimeEntryChange getChange() { return change; }
    public void setChange(TimeEntryChange change) { this.change = change; }

    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID changedBy) { this.changedBy = changedBy; }

    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public OffsetDateTime getPreviousHappenedAt() { return previousHappenedAt; }
    public void setPreviousHappenedAt(OffsetDateTime previousHappenedAt) { this.previousHappenedAt = previousHappenedAt; }

    public TimeDirection getPreviousDirection() { return previousDirection; }
    public void setPreviousDirection(TimeDirection previousDirection) { this.previousDirection = previousDirection; }

    public UUID getPreviousEnterpriseId() { return previousEnterpriseId; }
    public void setPreviousEnterpriseId(UUID previousEnterpriseId) { this.previousEnterpriseId = previousEnterpriseId; }

    public String getPreviousNote() { return previousNote; }
    public void setPreviousNote(String previousNote) { this.previousNote = previousNote; }

    public OffsetDateTime getPreviousDeletedAt() { return previousDeletedAt; }
    public void setPreviousDeletedAt(OffsetDateTime previousDeletedAt) { this.previousDeletedAt = previousDeletedAt; }

    /** Fotografia do estado atual de uma picagem, para guardar antes de a alterar. */
    public static TimeEntryRevision before(TimeEntry entry, TimeEntryChange change,
                                           UUID changedBy, String changedByName, String reason) {
        TimeEntryRevision revision = new TimeEntryRevision();
        revision.timeEntryId = entry.getId();
        revision.change = change;
        revision.changedBy = changedBy;
        revision.changedByName = changedByName;
        revision.reason = reason;

        if (change != TimeEntryChange.CREATE) {
            revision.previousHappenedAt = entry.getHappenedAt();
            revision.previousDirection = entry.getDirection();
            revision.previousEnterpriseId = entry.getEnterprise() == null ? null : entry.getEnterprise().getId();
            revision.previousNote = entry.getNote();
            revision.previousDeletedAt = entry.getDeletedAt();
        }
        return revision;
    }
}
