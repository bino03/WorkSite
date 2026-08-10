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
 * Fatura de obra — o <b>documento</b>, não o lançamento.
 *
 * Existe agarrada ao projeto e não à rubrica, de propósito: quem chega da obra
 * com quinze faturas consegue registá-las todas sem decidir a classificação, e
 * classifica depois. Uma fatura sem {@link ConstructionExpense} associada é uma
 * fatura "por associar" — é essa a caixa de entrada.
 *
 * Os campos fiscais vêm do QR da AT ({@code AtInvoiceQrService}) e são todos
 * corrigíveis à mão. {@code invoiceDate} e {@code totalAmount} podem ficar a
 * null quando não há QR legível; nesse caso a fatura fica marcada como
 * {@link #needsReview()} e só pode ser associada depois de preenchida, porque
 * a despesa exige ambos.
 */
@Entity
@Table(name = "construction_invoice", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ConstructionInvoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    // ── dados do QR code da AT ──

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_nif")
    private String supplierNif;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_atcud")
    private String invoiceAtcud;

    /** Data da fatura. Null quando o QR não foi lido — ver {@link #needsReview()}. */
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "taxable_amount", precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", precision = 14, scale = 2)
    private BigDecimal taxAmount;

    private String notes;

    // ── documento (bucket + chave; a URL assinada é gerada na leitura) ──

    @Column(nullable = false)
    private String bucket;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type")
    private String mimeType;

    /** O que ficou no Storage — já depois da compressão feita no browser. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Tamanho antes da compressão, reportado pelo cliente. Só para mostrar a poupança. */
    @Column(name = "original_size_bytes")
    private Long originalSizeBytes;

    // ── miniatura para as listas ──

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "thumbnail_mime")
    private String thumbnailMime;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    // ── envio para a contabilidade ──

    @Column(name = "sent_to_accountant", nullable = false)
    private boolean sentToAccountant = false;

    @Column(name = "sent_to_accountant_by")
    private UUID sentToAccountantBy;

    @Column(name = "sent_to_accountant_at")
    private OffsetDateTime sentToAccountantAt;

    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * A fatura não tem o mínimo para virar lançamento. Acontece sempre que o QR
     * não foi lido — é estado normal de trabalho, não erro.
     */
    public boolean needsReview() {
        return invoiceDate == null || totalAmount == null;
    }

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierNif() { return supplierNif; }
    public void setSupplierNif(String supplierNif) { this.supplierNif = supplierNif; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getInvoiceAtcud() { return invoiceAtcud; }
    public void setInvoiceAtcud(String invoiceAtcud) { this.invoiceAtcud = invoiceAtcud; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

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

    public Long getOriginalSizeBytes() { return originalSizeBytes; }
    public void setOriginalSizeBytes(Long originalSizeBytes) { this.originalSizeBytes = originalSizeBytes; }

    public String getThumbnailKey() { return thumbnailKey; }
    public void setThumbnailKey(String thumbnailKey) { this.thumbnailKey = thumbnailKey; }

    public String getThumbnailMime() { return thumbnailMime; }
    public void setThumbnailMime(String thumbnailMime) { this.thumbnailMime = thumbnailMime; }

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
