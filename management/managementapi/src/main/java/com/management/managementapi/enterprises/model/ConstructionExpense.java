package com.management.managementapi.enterprises.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * Despesa lançada contra uma rubrica do orçamento.
 *
 * Leva os mesmos campos de medição da rubrica ({@code unit}, {@code quantity},
 * {@code unitPrice}, {@code totalPrice}, {@code observations}) para se poder
 * comparar o gasto real com o orçamentado linha a linha, mais a fatura e o
 * registo de envio para a contabilidade.
 */
@Entity
@Table(name = "construction_expense", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ConstructionExpense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_item_id")
    private ConstructionBudgetItem budgetItem;

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

    // ── dados do QR code da AT (ver AtInvoiceQrService) ──

    @Column(name = "supplier_nif")
    private String supplierNif;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_atcud")
    private String invoiceAtcud;

    // ── fatura (bucket + chave; a URL assinada é gerada na leitura) ──

    private String bucket;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    // ── envio para a contabilidade ──

    @Column(name = "sent_to_accountant", nullable = false)
    private boolean sentToAccountant = false;

    @Column(name = "sent_to_accountant_by")
    private UUID sentToAccountantBy;

    @Column(name = "sent_to_accountant_at")
    private OffsetDateTime sentToAccountantAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public ConstructionBudgetItem getBudgetItem() { return budgetItem; }
    public void setBudgetItem(ConstructionBudgetItem budgetItem) { this.budgetItem = budgetItem; }

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

    public String getSupplierNif() { return supplierNif; }
    public void setSupplierNif(String supplierNif) { this.supplierNif = supplierNif; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getInvoiceAtcud() { return invoiceAtcud; }
    public void setInvoiceAtcud(String invoiceAtcud) { this.invoiceAtcud = invoiceAtcud; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public boolean isSentToAccountant() { return sentToAccountant; }
    public void setSentToAccountant(boolean sentToAccountant) { this.sentToAccountant = sentToAccountant; }

    public UUID getSentToAccountantBy() { return sentToAccountantBy; }
    public void setSentToAccountantBy(UUID sentToAccountantBy) { this.sentToAccountantBy = sentToAccountantBy; }

    public OffsetDateTime getSentToAccountantAt() { return sentToAccountantAt; }
    public void setSentToAccountantAt(OffsetDateTime sentToAccountantAt) { this.sentToAccountantAt = sentToAccountantAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
