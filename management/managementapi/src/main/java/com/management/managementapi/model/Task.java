package com.management.managementapi.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.model.enums.TaskStatus;

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
 * tarefa atribuível a um ou mais utilizadores (profile).
 */
@Entity
@Table(name = "task", schema = "tasks")
public class Task extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date", nullable = false)
    private OffsetDateTime dueDate;

    // enum DB; String aqui
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "tasks.task_status_enum", nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Profile createdBy;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskAssignee> assignees = new ArrayList<>();

    // getters/setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getDueDate() { return dueDate; }
    public void setDueDate(OffsetDateTime dueDate) { this.dueDate = dueDate; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public Profile getCreatedBy() { return createdBy; }
    public void setCreatedBy(Profile createdBy) { this.createdBy = createdBy; }

    public List<TaskAssignee> getAssignees() { return assignees; }

    /**
     * Substitui a lista de utilizadores atribuídos por uma nova (usado no create/update).
     * Faz diff em vez de "clear + recriar tudo": o Hibernate corre os INSERTs antes dos
     * DELETEs de órfãos no mesmo flush, por isso recriar uma linha (task_id, profile_id)
     * que já existia violava a constraint UNIQUE antes do DELETE do registo antigo correr.
     */
    public void replaceAssignees(List<Profile> profiles) {
        Set<UUID> targetIds = profiles.stream().map(Profile::getId).collect(Collectors.toSet());

        this.assignees.removeIf(assignee -> !targetIds.contains(assignee.getProfile().getId()));

        Set<UUID> currentIds = this.assignees.stream()
                .map(assignee -> assignee.getProfile().getId())
                .collect(Collectors.toSet());

        for (Profile profile : profiles) {
            if (!currentIds.contains(profile.getId())) {
                TaskAssignee assignee = new TaskAssignee();
                assignee.setTask(this);
                assignee.setProfile(profile);
                this.assignees.add(assignee);
            }
        }
    }
}
