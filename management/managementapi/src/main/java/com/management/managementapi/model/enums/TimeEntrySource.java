package com.management.managementapi.model.enums;

/**
 * como a picagem foi registada. O método é um dado do registo, e os outros
 * (telemóvel com GPS, posto fixo) entram como valores novos sem mexer na tabela —
 * ver notes/roadmap/assiduidade.md, fase 5.
 */
public enum TimeEntrySource {
    /** Registada (ou corrigida) pelo admin no Backoffice. */
    MANUAL,
    /** O próprio funcionário leu o QR da obra (V49, 2026-11-20). */
    QR
}
