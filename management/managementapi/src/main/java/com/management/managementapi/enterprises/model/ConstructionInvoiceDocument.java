package com.management.managementapi.enterprises.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * Um ficheiro de uma fatura de obra.
 *
 * Até à V24 a fatura <i>era</i> o ficheiro: {@code bucket} e {@code storage_key}
 * eram NOT NULL na própria fatura. Isso deixava de fora os dois casos que o
 * vault da Vilatro tem todos os dias: a fatura ainda por pedir ou por imprimir
 * (nenhum ficheiro), e a fatura com a foto tirada na obra <i>e</i> o PDF que o
 * fornecedor mandou depois (mais do que um).
 *
 * A partir daqui a fatura é o registo, e isto são os seus 0..N documentos.
 * Ver docs/faturas-modelo-alvo.md, secção 2.2.
 */
@Entity
@Table(name = "construction_invoice_document", schema = "worksite")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class ConstructionInvoiceDocument extends BaseEntity {

    /** Só para a UI agrupar e ordenar — nenhuma regra de negócio decide com base nisto. */
    public enum Kind {
        ORIGINAL, PAGE, PHOTO, OTHER
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private ConstructionInvoice invoice;

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

    /**
     * SHA-256 do ficheiro em hexadecimal. Único <b>global</b> (V24): o mesmo
     * ficheiro não entra duas vezes em obra nenhuma, nem na quarentena, nem nas
     * despesas da empresa.
     */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kind", nullable = false, columnDefinition = "worksite.invoice_document_kind")
    private Kind kind = Kind.ORIGINAL;

    /** Só para PDFs partidos página a página. */
    @Column(name = "page_number")
    private Integer pageNumber;

    /**
     * Texto bruto do QR lido <b>deste</b> ficheiro, para auditoria. Os campos já
     * interpretados (NIF, número, ATCUD, totais) continuam na fatura.
     */
    @Column(name = "qr_payload")
    private String qrPayload;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    public ConstructionInvoice getInvoice() { return invoice; }
    public void setInvoice(ConstructionInvoice invoice) { this.invoice = invoice; }

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

    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getQrPayload() { return qrPayload; }
    public void setQrPayload(String qrPayload) { this.qrPayload = qrPayload; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
