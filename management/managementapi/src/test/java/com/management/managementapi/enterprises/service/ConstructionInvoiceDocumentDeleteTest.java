package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Remover <b>um</b> documento é diferente de substituir o ficheiro: o
 * {@code POST /{id}/file} larga todos e põe um só no lugar, enquanto isto tira
 * exatamente a linha indicada e deixa as outras.
 *
 * <p>O que estes testes protegem é a regra que liga as duas tabelas: o estado do
 * papel segue o papel. Tirar o último documento tem de devolver a fatura a
 * {@code MISSING}, porque {@code ARCHIVED} sem ficheiro seria mentira — e tirar
 * um de vários não pode mexer no estado.
 */
@ExtendWith(MockitoExtension.class)
class ConstructionInvoiceDocumentDeleteTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private AtInvoiceQrService qrService;
    @Mock private InvoiceThumbnailService thumbnailService;
    @Mock private InvoiceCompressionService compressionService;
    @Mock private AuthContext authContext;
    @Mock private NotificationService notifications;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    private static ConstructionInvoiceDocument document(ConstructionInvoice invoice, UUID id) {
        ConstructionInvoiceDocument document = new ConstructionInvoiceDocument();
        document.setId(id);
        document.setInvoice(invoice);
        document.setBucket("documents");
        document.setStorageKey("construction-invoices/obra/fatura.pdf");
        document.setThumbnailKey("construction-invoices/obra/fatura-thumb.jpg");
        return document;
    }

    @Test
    @DisplayName("tirar o último documento devolve a fatura ao estado 'sem ficheiro'")
    void lastDocumentRemovedGoesBackToMissing() throws IOException {
        UUID invoiceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(invoiceId);
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.ARCHIVED);

        when(repository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(documentRepository.findByIdAndInvoiceId(documentId, invoiceId))
                .thenReturn(Optional.of(document(invoice, documentId)));
        when(documentRepository.countByInvoiceId(invoiceId)).thenReturn(0L);

        service.deleteDocument(invoiceId, documentId);

        // O ficheiro e a miniatura saem os dois do Storage, não só o ficheiro.
        verify(storageService).delete("documents", "construction-invoices/obra/fatura.pdf");
        verify(storageService).delete("documents", "construction-invoices/obra/fatura-thumb.jpg");
        assertThat(invoice.getDocumentStatus())
                .isEqualTo(ConstructionInvoice.DocumentStatus.MISSING);
    }

    @Test
    @DisplayName("tirar um de vários deixa o estado da fatura como estava")
    void remainingDocumentsKeepStatus() {
        UUID invoiceId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(invoiceId);
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.ARCHIVED);

        when(repository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(documentRepository.findByIdAndInvoiceId(documentId, invoiceId))
                .thenReturn(Optional.of(document(invoice, documentId)));
        when(documentRepository.countByInvoiceId(invoiceId)).thenReturn(2L);

        service.deleteDocument(invoiceId, documentId);

        assertThat(invoice.getDocumentStatus())
                .isEqualTo(ConstructionInvoice.DocumentStatus.ARCHIVED);
    }

    @Test
    @DisplayName("um documento de outra fatura não é apagado por engano")
    void documentOfAnotherInvoiceIsNotFound() throws IOException {
        UUID invoiceId = UUID.randomUUID();
        UUID foreignDocumentId = UUID.randomUUID();

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(invoiceId);

        when(repository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(documentRepository.findByIdAndInvoiceId(foreignDocumentId, invoiceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDocument(invoiceId, foreignDocumentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVOICE_DOCUMENT_NOT_FOUND));

        verify(storageService, never()).delete(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(documentRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
