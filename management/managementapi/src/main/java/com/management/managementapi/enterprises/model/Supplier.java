package com.management.managementapi.enterprises.model;

import java.util.UUID;

import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Fornecedor de obra — o par NIF → nome da empresa.
 *
 * Existe porque o QR da AT identifica o emitente <b>só pelo NIF</b>: não há
 * campo para o nome na especificação, por isso todas as faturas lidas chegam
 * com {@code supplierNif} preenchido e {@code supplierName} vazio. Sem
 * catálogo, o nome era escrito à mão uma vez por fatura.
 *
 * É global, não por projeto: o mesmo NIF é a mesma empresa em todas as obras.
 * Por isso também não tem {@code enterprise_id} — a ligação às faturas faz-se
 * pelo NIF, não por chave estrangeira, para uma fatura poder existir com um
 * fornecedor que ainda não está no catálogo (que é o estado normal de uma
 * fatura acabada de carregar).
 */
@Entity
@Table(name = "supplier", schema = "worksite")
public class Supplier extends BaseEntity {

    @Column(name = "nif", nullable = false)
    private String nif;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
