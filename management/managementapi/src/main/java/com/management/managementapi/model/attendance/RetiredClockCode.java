package com.management.managementapi.model.attendance;

import java.time.OffsetDateTime;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * um código de QR de picagem que já foi substituído por "Gerar novo QR" (V52).
 * Existe para distinguir um QR <em>antigo</em> (aviso aos ADMIN) de um código
 * <em>inventado</em> (só {@code ATT_020}).
 */
@Entity
@Table(name = "retired_clock_code", schema = "attendance")
public class RetiredClockCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private Enterprise enterprise;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "retired_at", nullable = false)
    private OffsetDateTime retiredAt;

    protected RetiredClockCode() {
    }

    /** A fotografia do código atual da obra, no momento em que é substituído. */
    public static RetiredClockCode of(EnterpriseClockCode current, OffsetDateTime retiredAt) {
        RetiredClockCode retired = new RetiredClockCode();
        retired.enterprise = current.getEnterprise();
        retired.code = current.getCode();
        retired.generatedAt = current.getGeneratedAt();
        retired.retiredAt = retiredAt;
        return retired;
    }

    public Enterprise getEnterprise() { return enterprise; }
    public String getCode() { return code; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public OffsetDateTime getRetiredAt() { return retiredAt; }
}
