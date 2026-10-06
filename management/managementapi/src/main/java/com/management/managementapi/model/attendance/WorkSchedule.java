package com.management.managementapi.model.attendance;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.management.managementapi.model.BaseEntity;
import com.management.managementapi.model.Profile;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * horário de trabalho reutilizável, atribuível a funcionários ("08–17 c/ 1h almoço").
 * Os dias da semana que o compõem estão em {@link WorkScheduleDay}; um dia que não
 * tenha linha não é dia de trabalho.
 */
@Entity
@Table(name = "work_schedule", schema = "attendance")
public class WorkSchedule extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Profile createdBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WorkScheduleDay> days = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Profile getCreatedBy() { return createdBy; }
    public void setCreatedBy(Profile createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public List<WorkScheduleDay> getDays() { return days; }
    public void setDays(List<WorkScheduleDay> days) { this.days = days; }
}
