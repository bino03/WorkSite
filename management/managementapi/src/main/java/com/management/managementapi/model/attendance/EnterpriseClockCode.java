package com.management.managementapi.model.attendance;

import java.time.OffsetDateTime;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * o código que vai dentro do QR de picagem de uma obra (V49). Opaco de propósito:
 * o id da obra não se muda, e um QR fotografado e partilhado tem de se poder
 * invalidar sem mexer na obra — gera-se um código novo e o antigo deixa de picar.
 */
@Entity
@Table(name = "enterprise_clock_code", schema = "attendance")
public class EnterpriseClockCode extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enterprise_id", nullable = false, unique = true)
    private Enterprise enterprise;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    /** Quando foi gerado o código atual (V50) — muda a cada "Gerar novo QR", ao contrário do created_at. */
    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public String getCode() { return code; }

    /** Um código novo é sempre um QR gerado agora: as duas coisas não se separam. */
    public void regenerate(String code, OffsetDateTime at) {
        this.code = code;
        this.generatedAt = at;
    }

    public OffsetDateTime getGeneratedAt() { return generatedAt; }
}
