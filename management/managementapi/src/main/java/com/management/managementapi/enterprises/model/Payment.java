package com.management.managementapi.enterprises.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.management.managementapi.enterprises.model.enums.PaymentMethod;
import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Um movimento de dinheiro que liquida faturas.
 *
 * No caso normal cobre uma fatura; no agregado (raro) cobre várias numa
 * transferência só — o fornecedor recebe N faturas de uma vez (decisão 23 do
 * Vilatro). Cada ligação vive em {@link InvoicePayment}; o estado da fatura
 * (UNPAID/PARTIAL/PAID) é derivado dessas ligações, nunca guardado.
 *
 * Ver docs/faturas-modelo-alvo.md §2.3.
 */
@Entity
@Table(name = "payment", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Payment extends BaseEntity {

    /** A data em que o dinheiro saiu — do extrato, do recibo, ou a que o utilizador disser. */
    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "method", nullable = false, columnDefinition = "worksite.payment_method")
    private PaymentMethod method;

    /** O valor do movimento. No caso normal = líquido da fatura; no agregado = soma das ligações. */
    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    /** Nº do movimento, "extrato ABANCA 28-08-2026", nome de quem pagou por conta da empresa... */
    @Column(name = "reference")
    private String reference;

    /**
     * O que a app não deduz: "pago pela Tabuada Pioneira", "desconto de 2% por
     * pronto pagamento", "inclui a caução". "Pagas juntas" nunca se escreve aqui
     * — é um facto estrutural (N ligações) que a UI gera sozinha.
     */
    @Column(name = "notes")
    private String notes;

    // ── prova (recibo ou página do extrato); um ficheiro chega. Nunca a URL. ──

    @Column(name = "proof_bucket")
    private String proofBucket;

    @Column(name = "proof_key")
    private String proofKey;

    @Column(name = "proof_filename")
    private String proofFilename;

    @Column(name = "proof_mime")
    private String proofMime;

    /** Quem marcou como pago. FK → profile, {@code on delete set null}. */
    @Column(name = "registered_by")
    private UUID registeredBy;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getProofBucket() { return proofBucket; }
    public void setProofBucket(String proofBucket) { this.proofBucket = proofBucket; }

    public String getProofKey() { return proofKey; }
    public void setProofKey(String proofKey) { this.proofKey = proofKey; }

    public String getProofFilename() { return proofFilename; }
    public void setProofFilename(String proofFilename) { this.proofFilename = proofFilename; }

    public String getProofMime() { return proofMime; }
    public void setProofMime(String proofMime) { this.proofMime = proofMime; }

    public UUID getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(UUID registeredBy) { this.registeredBy = registeredBy; }

    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
}
