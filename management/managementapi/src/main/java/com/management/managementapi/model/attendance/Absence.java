package com.management.managementapi.model.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.management.managementapi.model.BaseEntity;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * uma ausência: férias, baixa, falta justificada ou injustificada.
 *
 * <p>Intervalo fechado nos dois extremos — {@code startsOn} e {@code endsOn} são
 * ambos dias de ausência. {@code halfDay} só se aplica quando os dois são o mesmo
 * dia: "meio dia" num intervalo de cinco não quer dizer nada.
 */
@Entity
@Table(name = "absence", schema = "attendance")
public class Absence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance.absence_type", nullable = false)
    private AbsenceType type;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "half_day", columnDefinition = "attendance.absence_half_day", nullable = false)
    private AbsenceHalfDay halfDay = AbsenceHalfDay.NONE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance.absence_status", nullable = false)
    private AbsenceStatus status = AbsenceStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private Profile requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Profile approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "absence", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AbsenceDocument> documents = new ArrayList<>();

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    public AbsenceType getType() { return type; }
    public void setType(AbsenceType type) { this.type = type; }

    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }

    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }

    public AbsenceHalfDay getHalfDay() { return halfDay; }
    public void setHalfDay(AbsenceHalfDay halfDay) { this.halfDay = halfDay; }

    public AbsenceStatus getStatus() { return status; }
    public void setStatus(AbsenceStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Profile getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Profile requestedBy) { this.requestedBy = requestedBy; }

    public Profile getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Profile approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public List<AbsenceDocument> getDocuments() { return documents; }
    public void setDocuments(List<AbsenceDocument> documents) { this.documents = documents; }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }

    /**
     * Não há {@code dayCount()} aqui de propósito. O saldo de férias conta **dias
     * úteis** — 22 por ano, o mínimo legal — e por isso depende do horário do
     * funcionário (que dias da semana trabalha) e dos feriados, que a entidade não
     * conhece. Contar dias de calendário daria saldos errados a quem atravessa um
     * fim de semana ou um feriado. A contagem vive no
     * {@code VacationBalanceService}, que tem as três coisas.
     */
}
