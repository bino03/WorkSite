package com.management.managementapi.model.attendance;

import java.time.LocalDate;
import java.util.UUID;

import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * uma tentativa de picar com um {@link RetiredClockCode} (V52). Uma por funcionário,
 * código e dia — a unique da tabela é o dedupe do aviso aos ADMIN.
 */
@Entity
@Table(name = "retired_clock_code_attempt", schema = "attendance")
public class RetiredClockCodeAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retired_code_id", nullable = false)
    private RetiredClockCode retiredCode;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "attempted_on", nullable = false)
    private LocalDate attemptedOn;

    protected RetiredClockCodeAttempt() {
    }

    public RetiredClockCodeAttempt(RetiredClockCode retiredCode, UUID profileId, LocalDate attemptedOn) {
        this.retiredCode = retiredCode;
        this.profileId = profileId;
        this.attemptedOn = attemptedOn;
    }

    public RetiredClockCode getRetiredCode() { return retiredCode; }
    public UUID getProfileId() { return profileId; }
    public LocalDate getAttemptedOn() { return attemptedOn; }
}
