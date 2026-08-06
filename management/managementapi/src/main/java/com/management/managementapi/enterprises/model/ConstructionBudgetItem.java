package com.management.managementapi.enterprises.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Rubrica do orçamento da obra — um nó da árvore.
 *
 * A profundidade é livre ({@code parent_id} aponta para a própria tabela) porque
 * a numeração dos orçamentos varia: o orçamento da Villa Petrus tem rubricas
 * a 4 níveis ({@code 17.1.5}) e sub-títulos sem numeração pelo meio.
 *
 * Os campos espelham 1:1 as colunas do Excel:
 * {@code Art · Descrição · Un. · Quant · Preço Un · Preço total · Obs.}
 */
@Entity
@Table(name = "construction_budget_item", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ConstructionBudgetItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ConstructionBudgetItem parent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "row_kind", nullable = false, columnDefinition = "worksite.budget_row_kind")
    private BudgetRowKind rowKind = BudgetRowKind.ITEM;

    /** Coluna A "Art" — "4.2.1" tal como no Excel. Null em títulos, notas e alternativas. */
    private String code;

    /** Ordem dentro do pai — reproduz a ordem das linhas no Excel. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Coluna B "Descrição". */
    @Column(nullable = false)
    private String name;

    /** Coluna C "Un." — só preenchida em 77 das 174 rubricas do orçamento original. */
    private String unit;

    /** Coluna D "Quant". */
    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    /** Coluna E "Preço Un". */
    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /** Coluna F "Preço total". Null quando a alternativa não foi a escolhida. */
    @Column(name = "total_price", precision = 14, scale = 2)
    private BigDecimal totalPrice;

    /** Coluna G "Obs.". */
    private String observations;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_by")
    private UUID createdBy;

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public ConstructionBudgetItem getParent() { return parent; }
    public void setParent(ConstructionBudgetItem parent) { this.parent = parent; }

    public BudgetRowKind getRowKind() { return rowKind; }
    public void setRowKind(BudgetRowKind rowKind) { this.rowKind = rowKind; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
