package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O checksum é a chave de duplicado que não depende de nada ter sido lido —
 * nem QR, nem preenchimento à mão.
 *
 * notes/bugs.md, caso 3: duas cópias byte-a-byte iguais do mesmo ficheiro
 * ("10.46.20.jpg" / "10.46.19.jpg" nos dados reais) passavam as duas porque,
 * sem QR legível, o ATCUD e o par (NIF, número) ficam ambos vazios e
 * {@code rejectIfDuplicate} não tinha por onde comparar.
 *
 * Desde a V24 o checksum vive no documento e a verificação é <b>global</b>:
 * o mesmo ficheiro é recusado esteja a outra fatura na obra que estiver.
 */
@ExtendWith(MockitoExtension.class)
class ConstructionInvoiceChecksumTest {

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

    @InjectMocks private ConstructionInvoiceService service;

    @Test
    @DisplayName("recusa o mesmo ficheiro carregado duas vezes, mesmo sem QR legível")
    void rejectsByteIdenticalFileWithoutQr() throws IOException {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);
        enterprise.setName("Vila Petrus");

        MockMultipartFile file = new MockMultipartFile(
                "file", "fatura.pdf", "application/pdf", "conteudo-do-ficheiro".getBytes());

        ConstructionInvoice existing = new ConstructionInvoice();
        existing.setId(UUID.randomUUID());
        existing.setEnterprise(enterprise);
        existing.setSupplierName("Leroy Merlin");

        ConstructionInvoiceDocument existingDocument = new ConstructionInvoiceDocument();
        existingDocument.setInvoice(existing);
        existingDocument.setOriginalFilename("fatura-antiga.pdf");

        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(enterprise));
        when(qrService.read(any(), any())).thenReturn(Optional.empty());
        when(documentRepository.findByChecksum(any(), any()))
                .thenReturn(List.of(existingDocument));

        assertThatThrownBy(() -> service.upload(enterpriseId, file))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVOICE_DUPLICATE_FILE))
                // A mensagem tem de dizer onde está a primeira: a colisão pode
                // agora vir de outra obra qualquer.
                .hasMessageContaining("Vila Petrus");

        // A rejeição tem de acontecer antes de qualquer escrita no Storage.
        verify(storageService, never()).upload(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deixa passar quando o checksum não existe em lado nenhum, e grava-o no documento")
    void acceptsNewChecksumAndPersistsIt() throws IOException {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "fatura.pdf", "application/pdf", "outro-conteudo-qualquer".getBytes());

        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(enterprise));
        when(qrService.read(any(), any())).thenReturn(Optional.empty());
        when(documentRepository.findByChecksum(any(), any())).thenReturn(List.of());
        when(repository.save(any(ConstructionInvoice.class))).thenAnswer(call -> call.getArgument(0));
        when(documentRepository.save(any(ConstructionInvoiceDocument.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.upload(enterpriseId, file);

        ArgumentCaptor<ConstructionInvoiceDocument> captor =
                ArgumentCaptor.forClass(ConstructionInvoiceDocument.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getChecksumSha256())
                .isNotBlank()
                .hasSize(64); // SHA-256 em hexadecimal
        assertThat(captor.getValue().getInvoice()).isNotNull();
    }
}
