package com.management.managementapi.enterprises.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Um lote (edifício) do projeto, com o seu próprio orçamento.
 *
 * Uma vila pode ter vários edifícios, cada um com o orçamento do empreiteiro e a
 * sua numeração — o {@code 4.2.1} do Lote A e o do Lote B são rubricas
 * diferentes. As faturas continuam no projeto; é a rubrica escolhida na
 * repartição que diz de que lote é o gasto (V39).
 */
@Entity
@Table(name = "construction_budget", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ConstructionBudget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
