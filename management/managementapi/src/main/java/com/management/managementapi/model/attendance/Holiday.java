package com.management.managementapi.model.attendance;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.management.managementapi.model.BaseEntity;
import com.management.managementapi.model.enums.HolidayScope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * um feriado. É uma tabela e não uma biblioteca de código porque os municipais
 * variam por concelho e mudam de ano para ano — uma tabela que o utilizador edita
 * bate código que alguém tem de ir corrigir todos os anos.
 */
@Entity
@Table(name = "holiday", schema = "attendance")
public class Holiday extends BaseEntity {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "attendance.holiday_scope", nullable = false)
    private HolidayScope scope;

    /** Só nos municipais; null nos nacionais. */
    @Column(name = "municipality")
    private String municipality;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public HolidayScope getScope() { return scope; }
    public void setScope(HolidayScope scope) { this.scope = scope; }

    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }
}
