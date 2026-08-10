package com.management.managementapi.enterprises.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Despesa lançada contra uma rubrica do orçamento — a <b>afetação</b> de um
 * gasto a uma linha da obra.
 *
 * Leva os mesmos campos de medição da rubrica ({@code unit}, {@code quantity},
 * {@code unitPrice}, {@code totalPrice}, {@code observations}) para se poder
 * comparar o gasto real com o orçamentado linha a linha.
 *
 * O <b>documento</b> vive em {@link ConstructionInvoice}, não aqui: ficheiro,
 * dados do QR da AT e envio para a contabilidade são propriedades da fatura, e
 * mantê-los na despesa impedia que uma fatura existisse antes de se saber a que
 * rubrica pertence. {@link #invoice} fica a null quando a despesa foi lançada à
 * mão, sem documento associado.
 */
@Entity
@Table(name = "construction_expense", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ConstructionExpense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_item_id")
    private ConstructionBudgetItem budgetItem;

    /**
     * A fatura de onde este lançamento saiu. Null numa despesa registada à mão.
     * Uma fatura tem no máximo um lançamento — garantido pelo índice único
     * {@code uq_expense_invoice}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private ConstructionInvoice invoice;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * Data da fatura — deliberadamente distinta de {@code createdAt}, que é
     * apenas quando a despesa foi escrita na app. Sem esta separação, lançar
     * faturas atrasadas em bloco atira-as todas para o mês do registo.
     */
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    private String unit;

    @Column(precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice;

    private String observations;

    @Column(name = "created_by")
    private UUID createdBy;

    public ConstructionBudgetItem getBudgetItem() { return budgetItem; }
    public void setBudgetItem(ConstructionBudgetItem budgetItem) { this.budgetItem = budgetItem; }

    public ConstructionInvoice getInvoice() { return invoice; }
    public void setInvoice(ConstructionInvoice invoice) { this.invoice = invoice; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

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

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
