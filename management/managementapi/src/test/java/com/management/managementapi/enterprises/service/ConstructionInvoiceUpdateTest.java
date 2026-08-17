package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.ConstructionInvoiceUpsertDTO;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A correção manual não pode deitar fora o que o QR trouxe.
 *
 * O formulário do Backoffice só mostra fornecedor, número, ATCUD, data, total e
 * notas — a base tributável e os impostos aparecem lá como texto, "lido do QR".
 * Enquanto estiveram no DTO de edição, gravar qualquer correção enviava-os a
 * null e apagava-os da fatura.
 */
@ExtendWith(MockitoExtension.class)
class ConstructionInvoiceUpdateTest {

    @Mock private ConstructionInvoiceRepository repository;
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

    @InjectMocks private ConstructionInvoiceService service;

    @Test
    @DisplayName("corrigir um campo à mão não apaga a base tributável nem os impostos")
    void keepsTaxBreakdownWhenCorrectingByHand() {
        UUID id = UUID.randomUUID();

        // A fatura vive sempre agarrada a um projeto (FK NOT NULL): sem ele a
        // verificação de duplicado não tem onde procurar.
        Enterprise enterprise = new Enterprise();
        enterprise.setId(UUID.randomUUID());

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(id);
        invoice.setEnterprise(enterprise);
        invoice.setSupplierNif("509442013");
        invoice.setInvoiceDate(LocalDate.of(2026, 1, 15));
        invoice.setTotalAmount(new BigDecimal("14760.00"));
        invoice.setTaxableAmount(new BigDecimal("12000.00"));
        invoice.setTaxAmount(new BigDecimal("2760.00"));

        when(repository.findById(id)).thenReturn(Optional.of(invoice));
        when(expenseRepository.findByInvoiceId(id)).thenReturn(Optional.empty());
        // Preencher o número e o ATCUD muda a identidade do documento, logo o
        // update procura colisões. Aqui não há nenhuma.
        when(repository.findByEnterpriseAndAtcud(any(), any(), any())).thenReturn(List.of());
        when(repository.findByEnterpriseAndSupplierNif(any(), any(), any()))
                .thenReturn(List.of());
        when(repository.save(any(ConstructionInvoice.class))).thenAnswer(call -> call.getArgument(0));

        // só se preenche o nome do fornecedor, que o QR não traz
        ConstructionInvoice saved = service.update(id, new ConstructionInvoiceUpsertDTO(
                "Betão Liz", "509442013", "FT 2026/114", "CSDF7T5H-0114",
                LocalDate.of(2026, 1, 15), new BigDecimal("14760.00"), null));

        assertThat(saved.getSupplierName()).isEqualTo("Betão Liz");
        assertThat(saved.getTaxableAmount()).isEqualByComparingTo("12000.00");
        assertThat(saved.getTaxAmount()).isEqualByComparingTo("2760.00");
    }

    @Test
    @DisplayName("recusa um número escrito de forma diferente da mesma fatura já registada")
    void rejectsDuplicateDocumentNumberWrittenDifferently() {
        UUID id = UUID.randomUUID();
        Enterprise enterprise = new Enterprise();
        enterprise.setId(UUID.randomUUID());

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(id);
        invoice.setEnterprise(enterprise);
        invoice.setSupplierNif("509442013");

        ConstructionInvoice existing = new ConstructionInvoice();
        existing.setId(UUID.randomUUID());
        existing.setSupplierNif("509442013");
        // Mesmo número, escrito de forma diferente: maiúsculas, espaço a mais,
        // hífen em vez de barra.
        existing.setInvoiceNumber("ft2026-114");

        when(repository.findById(id)).thenReturn(Optional.of(invoice));
        when(repository.findByEnterpriseAndSupplierNif(any(), any(), any()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.update(id, new ConstructionInvoiceUpsertDTO(
                null, "509442013", "FT 2026/114", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVOICE_DUPLICATE_DOCUMENT));
    }
}
