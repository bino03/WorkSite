package com.management.managementapi.enterprises.model;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Uma inconsistência registada sobre uma ou mais faturas — tipicamente depois de
 * uma transferência atrapalhada, ou de um cruzamento manual que não bate certo.
 * O corpo é markdown livre.
 *
 * Não é a transferência que a cria: a resposta da transferência devolve
 * {@code suggestIncident=true} e o utilizador escreve-a à mão na página
 * "Inconsistências". Sem soft-delete — um incidente enganado apaga-se, um
 * resolvido fica com {@code resolvedAt}.
 *
 * Ver docs/faturas-modelo-alvo.md §4.
 */
@Entity
@Table(name = "invoice_incident", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class InvoiceIncident extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    /** Markdown. */
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    /** FK -> profile, {@code on delete set null}. */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /** FK -> profile, {@code on delete set null}. */
    @Column(name = "created_by")
    private UUID createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "invoice_incident_invoice", schema = "worksite",
        joinColumns = @JoinColumn(name = "incident_id"),
        inverseJoinColumns = @JoinColumn(name = "invoice_id")
    )
    private Set<ConstructionInvoice> invoices = new LinkedHashSet<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Set<ConstructionInvoice> getInvoices() { return invoices; }
    public void setInvoices(Set<ConstructionInvoice> invoices) { this.invoices = invoices; }
}
