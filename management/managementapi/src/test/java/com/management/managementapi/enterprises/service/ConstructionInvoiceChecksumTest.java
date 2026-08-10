package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
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
 */
@ExtendWith(MockitoExtension.class)
class ConstructionInvoiceChecksumTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private AtInvoiceQrService qrService;
    @Mock private InvoiceThumbnailService thumbnailService;
    @Mock private AuthContext authContext;

    @InjectMocks private ConstructionInvoiceService service;

    @Test
    @DisplayName("recusa o mesmo ficheiro carregado duas vezes, mesmo sem QR legível")
    void rejectsByteIdenticalFileWithoutQr() throws IOException {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "fatura.pdf", "application/pdf", "conteudo-do-ficheiro".getBytes());

        ConstructionInvoice existing = new ConstructionInvoice();
        existing.setId(UUID.randomUUID());
        existing.setOriginalFilename("fatura-antiga.pdf");

        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(enterprise));
        when(qrService.read(any(), any())).thenReturn(Optional.empty());
        when(repository.findByEnterpriseAndChecksum(any(), any(), any()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.upload(enterpriseId, file, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVOICE_DUPLICATE_FILE));

        // A rejeição tem de acontecer antes de qualquer escrita no Storage.
        verify(storageService, never()).upload(any(), any(), any(), any());
    }

    @Test
    @DisplayName("deixa passar quando o checksum é novo no projeto, e grava-o")
    void acceptsNewChecksumAndPersistsIt() throws IOException {
        UUID enterpriseId = UUID.randomUUID();
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "fatura.pdf", "application/pdf", "outro-conteudo-qualquer".getBytes());

        when(enterpriseRepository.findById(enterpriseId)).thenReturn(Optional.of(enterprise));
        when(qrService.read(any(), any())).thenReturn(Optional.empty());
        when(repository.findByEnterpriseAndChecksum(any(), any(), any())).thenReturn(List.of());
        when(repository.save(any(ConstructionInvoice.class))).thenAnswer(call -> call.getArgument(0));

        service.upload(enterpriseId, file, null);

        ArgumentCaptor<ConstructionInvoice> captor = ArgumentCaptor.forClass(ConstructionInvoice.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChecksumSha256())
                .isNotBlank()
                .hasSize(64); // SHA-256 em hexadecimal
    }
}
