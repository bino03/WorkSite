package com.management.managementapi.model.attendance;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.management.managementapi.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * um documento justificativo de uma {@link Absence} — uma baixa médica, um
 * comprovativo.
 *
 * <p>Tabela própria e não colunas na {@code absence} porque <b>pode chegar um
 * segundo ficheiro para a mesma ausência</b>: uma baixa de duas páginas, uma
 * prorrogação. É a pergunta que a skill {@code add-file-upload} manda fazer antes
 * de escolher, e foi ignorá-la que obrigou à {@code V24} nas faturas.
 *
 * <p>Guarda {@code bucket} + {@code storageKey}, nunca a URL nem os bytes. A URL
 * assinada gera-se na leitura.
 */
@Entity
@Table(name = "absence_document", schema = "attendance")
public class AbsenceDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "absence_id", nullable = false)
    private Absence absence;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    public Absence getAbsence() { return absence; }
    public void setAbsence(Absence absence) { this.absence = absence; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
