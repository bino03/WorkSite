package com.management.managementapi.enterprises.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * O recibo que o fornecedor emite (RC/RG no QR da AT), ligado às faturas que
 * liquida por {@link InvoiceReceipt}.
 *
 * Não mexe no estado de pagamento: esse deriva só de {@link InvoicePayment}.
 * Os pagamentos que o recibo originou (um por obra) ficam em
 * {@link SupplierReceiptPayment}. Ver notes/roadmap/recibos.md.
 */
@Entity
@Table(name = "supplier_receipt", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class SupplierReceipt extends BaseEntity {

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_nif")
    private String supplierNif;

    @Column(name = "receipt_number")
    private String receiptNumber;

    /** O nº só com letras/dígitos em maiúsculas — a chave do duplicado. */
    @Column(name = "receipt_number_normalized")
    private String receiptNumberNormalized;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "total_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "notes")
    private String notes;

    @Column(name = "file_bucket", nullable = false)
    private String fileBucket;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "file_filename")
    private String fileFilename;

    @Column(name = "file_mime")
    private String fileMime;

    @Column(name = "registered_by")
    private UUID registeredBy;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierNif() { return supplierNif; }
    public void setSupplierNif(String supplierNif) { this.supplierNif = supplierNif; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public String getReceiptNumberNormalized() { return receiptNumberNormalized; }
    public void setReceiptNumberNormalized(String receiptNumberNormalized) { this.receiptNumberNormalized = receiptNumberNormalized; }

    public LocalDate getReceiptDate() { return receiptDate; }
    public void setReceiptDate(LocalDate receiptDate) { this.receiptDate = receiptDate; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getFileBucket() { return fileBucket; }
    public void setFileBucket(String fileBucket) { this.fileBucket = fileBucket; }

    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }

    public String getFileFilename() { return fileFilename; }
    public void setFileFilename(String fileFilename) { this.fileFilename = fileFilename; }

    public String getFileMime() { return fileMime; }
    public void setFileMime(String fileMime) { this.fileMime = fileMime; }

    public UUID getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(UUID registeredBy) { this.registeredBy = registeredBy; }

    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
}
