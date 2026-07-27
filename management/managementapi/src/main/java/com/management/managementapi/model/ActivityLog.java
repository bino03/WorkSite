package com.management.managementapi.model;

import com.management.managementapi.model.converters.ActivityTypeConverter;
import com.management.managementapi.model.converters.EntityTypeConverter;
import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "activity_log",
    schema = "worksite",
    indexes = {
        @Index(name = "idx_activity_log_user_id",      columnList = "user_id"),
        @Index(name = "idx_activity_log_created_at",   columnList = "created_at DESC"),
        @Index(name = "idx_activity_log_activity_type",columnList = "activity_type"),
        @Index(name = "idx_activity_log_user_created", columnList = "user_id, created_at DESC")
    }
)
public class ActivityLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Convert(converter = ActivityTypeConverter.class)
    @Column(name = "activity_type", nullable = false, columnDefinition = "worksite.activity_type")
    private ActivityType activityType;

    @Convert(converter = EntityTypeConverter.class)
    @Column(name = "entity_type", columnDefinition = "worksite.entity_type")
    private EntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Stored as JSON text; JSONB type handled by PostgreSQL automatically
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
