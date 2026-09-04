package com.management.managementapi.enterprises.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.model.BaseEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * Onde é que esta fatura está. Antes da V26 só existia o primeiro caso — e a
     * obra era obrigatória, o que deixava de fora as despesas da empresa e a
     * quarentena do vault da Vilatro.
     */
    public enum Scope {
        /** De uma obra. Exige {@code enterprise}. */
        PROJECT,
        /** Da empresa, sem obra. */
        COMPANY,
        /** Quarentena: ainda não se sabe de quem é. */
        UNIDENTIFIED
    }

    /** Nula em {@code COMPANY} e {@code UNIDENTIFIED} — garantido por check na base de dados. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope", nullable = false, columnDefinition = "worksite.invoice_scope")
    private Scope scope = Scope.PROJECT;

    /** Só em {@code UNIDENTIFIED}: a coluna "Obras possíveis" da quarentena. Texto livre. */
    @Column(name = "possible_enterprises")
    private String possibleEnterprises;

    /** Só em {@code UNIDENTIFIED}: a coluna "Perguntar a" da quarentena. Texto livre. */
    @Column(name = "ask_whom")
    private String askWhom;

    /** Fatura ou nota de crédito. A lógica da nota de crédito é da fase 3; aqui é só o campo. */
    public enum DocumentType {
        INVOICE, CREDIT_NOTE
    }

    /**
     * O estado do papel. {@code ARCHIVED} deriva de haver documento, mas
     * {@code TO_PRINT} e {@code TO_REQUEST} são intenção — o que falta fazer,
     * não o que existe — e por isso ficam guardados.
     */
    public enum DocumentStatus {
        ARCHIVED, MISSING, TO_PRINT, TO_REQUEST
    }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "document_type", nullable = false, columnDefinition = "worksite.invoice_document_type")
    private DocumentType documentType = DocumentType.INVOICE;

    /** Obrigatório em {@code CREDIT_NOTE}, proibido em {@code INVOICE} — check na base de dados. */
    @Column(name = "related_invoice_id")
    private UUID relatedInvoiceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "document_status", nullable = false, columnDefinition = "worksite.invoice_document_status")
    private DocumentStatus documentStatus = DocumentStatus.MISSING;

    /** O "Produto/Serviço" do Excel. Existe sem ser preciso haver despesa. */
    @Column(name = "description")
    private String description;

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

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public String getPossibleEnterprises() { return possibleEnterprises; }
    public void setPossibleEnterprises(String possibleEnterprises) { this.possibleEnterprises = possibleEnterprises; }

    public String getAskWhom() { return askWhom; }
    public void setAskWhom(String askWhom) { this.askWhom = askWhom; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public UUID getRelatedInvoiceId() { return relatedInvoiceId; }
    public void setRelatedInvoiceId(UUID relatedInvoiceId) { this.relatedInvoiceId = relatedInvoiceId; }

    public DocumentStatus getDocumentStatus() { return documentStatus; }
    public void setDocumentStatus(DocumentStatus documentStatus) { this.documentStatus = documentStatus; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** O id da obra, ou null quando a fatura está na quarentena ou é da empresa. */
    public UUID getEnterpriseId() {
        return enterprise == null ? null : enterprise.getId();
    }

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

    public boolean isSentToAccountant() { return sentToAccountant; }
    public void setSentToAccountant(boolean sentToAccountant) { this.sentToAccountant = sentToAccountant; }

    public UUID getSentToAccountantBy() { return sentToAccountantBy; }
    public void setSentToAccountantBy(UUID sentToAccountantBy) { this.sentToAccountantBy = sentToAccountantBy; }

    public OffsetDateTime getSentToAccountantAt() { return sentToAccountantAt; }
    public void setSentToAccountantAt(OffsetDateTime sentToAccountantAt) { this.sentToAccountantAt = sentToAccountantAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
