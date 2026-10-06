package com.management.managementapi.model.attendance;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.model.BaseEntity;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntrySource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * uma picagem: quem, quando, entrada ou saída, por que meio, e em que obra.
 *
 * <p>É a tabela que não pode ter de ser refeita. {@code source} guarda o
 * <em>método</em> como dado, para que o QR ou um posto fixo entrem depois sem
 * migração; {@code enterprise} vive em cada picagem e não no dia, porque o QR por
 * obra traz a obra de graça no momento da picagem.
 *
 * <p>{@code happenedAt} é um instante ({@code timestamptz}); o dia a que pertence
 * depende do fuso e calcula-se com {@code Europe/Lisbon} explícito, nunca em UTC.
 */
@Entity
@Table(name = "time_entry", schema = "attendance")
public class TimeEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @Column(name = "happened_at", nullable = false)
    private OffsetDateTime happenedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance.time_direction", nullable = false)
    private TimeDirection direction;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance.time_entry_source", nullable = false)
    private TimeEntrySource source = TimeEntrySource.MANUAL;

    /** Null = registada pelo próprio. Na fase 1 (só admin) fica sempre preenchida. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_by")
    private Profile registeredBy;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public OffsetDateTime getHappenedAt() { return happenedAt; }
    public void setHappenedAt(OffsetDateTime happenedAt) { this.happenedAt = happenedAt; }

    public TimeDirection getDirection() { return direction; }
    public void setDirection(TimeDirection direction) { this.direction = direction; }

    public TimeEntrySource getSource() { return source; }
    public void setSource(TimeEntrySource source) { this.source = source; }

    public Profile getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(Profile registeredBy) { this.registeredBy = registeredBy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
