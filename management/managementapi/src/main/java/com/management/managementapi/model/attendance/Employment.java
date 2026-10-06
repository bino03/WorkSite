package com.management.managementapi.model.attendance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.management.managementapi.model.BaseEntity;
import com.management.managementapi.model.Profile;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * o vínculo de um funcionário: o que não muda ao longo do tempo. O horário e os
 * dias de férias mudam e vivem em {@link EmploymentTerm}, com datas — meter tudo
 * aqui obrigaria a repetir a admissão em cada período.
 *
 * <p>Separado de {@code worksite.profile} de propósito: o perfil é identidade e
 * login, e um admin pode não ter dados de emprego nenhuns.
 */
@Entity
@Table(name = "employment", schema = "attendance")
public class Employment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private Profile profile;

    @Column(name = "hired_at", nullable = false)
    private LocalDate hiredAt;

    /** Fim do vínculo. Null = ainda cá está. Não apaga nada do que ficou registado. */
    @Column(name = "ended_at")
    private LocalDate endedAt;

    @OneToMany(mappedBy = "employment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EmploymentTerm> terms = new ArrayList<>();

    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }

    public LocalDate getHiredAt() { return hiredAt; }
    public void setHiredAt(LocalDate hiredAt) { this.hiredAt = hiredAt; }

    public LocalDate getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDate endedAt) { this.endedAt = endedAt; }

    public List<EmploymentTerm> getTerms() { return terms; }
    public void setTerms(List<EmploymentTerm> terms) { this.terms = terms; }

    /** O período em vigor: o único sem {@code validTo}. */
    public EmploymentTerm currentTerm() {
        return terms.stream().filter(term -> term.getValidTo() == null).findFirst().orElse(null);
    }

    /** O período que estava em vigor numa data — é isto que faz o passado não mudar. */
    public EmploymentTerm termOn(LocalDate date) {
        return terms.stream().filter(term -> term.covers(date)).findFirst().orElse(null);
    }
}
