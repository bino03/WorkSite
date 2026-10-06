package com.management.managementapi.mapper.attendance;

import java.util.List;

import org.springframework.stereotype.Component;

import com.management.managementapi.dto.attendance.response.AbsenceDocumentResponseDTO;
import com.management.managementapi.dto.attendance.response.AbsenceResponseDTO;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.model.attendance.Absence;
import com.management.managementapi.model.attendance.AbsenceDocument;

/**
 * Componente Spring e não um mapper MapStruct porque precisa do
 * {@link SignedUrlService}: as URLs dos justificativos são assinadas na leitura e
 * nunca guardadas, e isso é uma dependência, não um mapeamento de campos.
 *
 * <p>Na <b>lista</b> os documentos não vêm (lista vazia) e no <b>detalhe</b> vêm
 * assinados: assinar os justificativos de um ano de ausências que ninguém vai abrir
 * é trabalho deitado fora, pela mesma razão que nas faturas.
 */
@Component
public class AbsenceMapper {

    private final SignedUrlService signedUrls;

    public AbsenceMapper(SignedUrlService signedUrls) {
        this.signedUrls = signedUrls;
    }

    /** Para listas: sem documentos, logo sem URLs assinadas. */
    public AbsenceResponseDTO toSummary(Absence absence, double workingDays) {
        return build(absence, workingDays, List.of());
    }

    /** Para o detalhe: com os documentos e as suas URLs assinadas. */
    public AbsenceResponseDTO toDetail(Absence absence, double workingDays) {
        List<AbsenceDocumentResponseDTO> documents = absence.getDocuments().stream()
                .map(this::toDocument)
                .toList();
        return build(absence, workingDays, documents);
    }

    public AbsenceDocumentResponseDTO toDocument(AbsenceDocument document) {
        return new AbsenceDocumentResponseDTO(
                document.getId(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getUploadedAt(),
                signedUrls.resolve(document.getBucket(), document.getStorageKey()));
    }

    private AbsenceResponseDTO build(Absence absence, double workingDays,
                                     List<AbsenceDocumentResponseDTO> documents) {
        return new AbsenceResponseDTO(
                absence.getId(),
                absence.getProfile().getId(),
                absence.getProfile().getName(),
                absence.getType(),
                absence.getStartsOn(),
                absence.getEndsOn(),
                absence.getHalfDay(),
                absence.getStatus(),
                absence.getNote(),
                absence.getRequestedBy() == null ? null : absence.getRequestedBy().getName(),
                absence.getApprovedBy() == null ? null : absence.getApprovedBy().getName(),
                absence.getApprovedAt(),
                workingDays,
                documents,
                absence.getDeletedAt());
    }
}
