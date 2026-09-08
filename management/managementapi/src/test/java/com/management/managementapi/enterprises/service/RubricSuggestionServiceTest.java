package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.response.RubricSuggestionDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * A sugestão de rubrica e — sobretudo — o <b>porquê</b> que a acompanha.
 *
 * Sem o porquê, uma sugestão é um palpite que se aceita sem pensar, e
 * classificar mal vai direto ao gasto por rubrica. As invariantes:
 *
 * <ol>
 *   <li>o histórico <b>desta obra</b> ganha sempre ao de outra;</li>
 *   <li>é "a mais usada", não "a última": uma classificação errada isolada não
 *       passa a mandar na sugestão;</li>
 *   <li>o palpite de outra obra atravessa pelo <b>código</b>, e só vale se esta
 *       obra tiver esse código;</li>
 *   <li>sem NIF, sem obra ou sem histórico não há sugestão nenhuma (204).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RubricSuggestionServiceTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private SupplierRepository supplierRepository;
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

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID ENTERPRISE_ID = UUID.randomUUID();
    private static final UUID RUBRIC_ID = UUID.randomUUID();

    private ConstructionInvoice invoice(String nif) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(INVOICE_ID);
        inv.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise e = new Enterprise();
        e.setId(ENTERPRISE_ID);
        inv.setEnterprise(e);
        inv.setSupplierName("Casa Dolores");
        inv.setSupplierNif(nif);
        when(repository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        return inv;
    }

    private ConstructionBudgetItem rubric(String code, String name) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(RUBRIC_ID);
        item.setCode(code);
        item.setName(name);
        item.setRowKind(BudgetRowKind.ITEM);
        return item;
    }

    private ConstructionInvoiceRepository.SupplierRubricUse use(long uses, long total) {
        return new ConstructionInvoiceRepository.SupplierRubricUse() {
            public UUID getBudgetItemId() { return RUBRIC_ID; }
            public long getUses() { return uses; }
            public long getTotalUses() { return total; }
        };
    }

    private ConstructionInvoiceRepository.SupplierRubricCodeUse codeUse(String code, String enterprise) {
        return new ConstructionInvoiceRepository.SupplierRubricCodeUse() {
            public String getCode() { return code; }
            public long getUses() { return 3; }
            public String getEnterpriseName() { return enterprise; }
        };
    }

    @Test
    @DisplayName("histórico nesta obra → HISTORY_PROJECT, com a contagem no porquê")
    void historicoDestaObra() {
        invoice("500100200");
        when(repository.findRubricUsesBySupplier(eq(ENTERPRISE_ID), eq("500100200"), any()))
                .thenReturn(List.of(use(4, 5)));
        when(budgetItemRepository.findById(RUBRIC_ID)).thenReturn(Optional.of(rubric("5.1.2", "Loiças")));

        RubricSuggestionDTO suggestion = service.suggestRubric(INVOICE_ID).orElseThrow();

        assertThat(suggestion.source()).isEqualTo("HISTORY_PROJECT");
        assertThat(suggestion.budgetItemId()).isEqualTo(RUBRIC_ID);
        assertThat(suggestion.explanation())
                .isEqualTo("4 das 5 faturas de Casa Dolores nesta obra foram para 5.1.2.");
        assertThat(suggestion.referenceEnterprise()).isNull();
    }

    @Test
    @DisplayName("quando foram todas para a mesma rubrica, o porquê di-lo sem contas")
    void historicoUnanime() {
        invoice("500100200");
        when(repository.findRubricUsesBySupplier(eq(ENTERPRISE_ID), eq("500100200"), any()))
                .thenReturn(List.of(use(5, 5)));
        when(budgetItemRepository.findById(RUBRIC_ID)).thenReturn(Optional.of(rubric("5.1.2", "Loiças")));

        assertThat(service.suggestRubric(INVOICE_ID).orElseThrow().explanation())
                .isEqualTo("Todas as faturas de Casa Dolores nesta obra foram para 5.1.2.");
    }

    @Test
    @DisplayName("sem histórico aqui, mas com o mesmo código noutra obra → HISTORY_GLOBAL")
    void historicoNoutraObraPeloCodigo() {
        invoice("500100200");
        when(repository.findRubricUsesBySupplier(any(), any(), any())).thenReturn(List.of());
        when(repository.findRubricCodesUsedElsewhere(eq(ENTERPRISE_ID), eq("500100200"), any()))
                .thenReturn(List.of(codeUse("4.2.1", "Vila Petrus")));
        when(budgetItemRepository.findByEnterpriseIdAndCode(ENTERPRISE_ID, "4.2.1"))
                .thenReturn(Optional.of(rubric("4.2.1", "Betão")));

        RubricSuggestionDTO suggestion = service.suggestRubric(INVOICE_ID).orElseThrow();

        assertThat(suggestion.source()).isEqualTo("HISTORY_GLOBAL");
        assertThat(suggestion.referenceEnterprise()).isEqualTo("Vila Petrus");
        assertThat(suggestion.explanation()).contains("Vila Petrus", "4.2.1");
    }

    @Test
    @DisplayName("o código de outra obra não existe nesta → sem sugestão")
    void codigoQueNaoExisteNestaObra() {
        invoice("500100200");
        when(repository.findRubricUsesBySupplier(any(), any(), any())).thenReturn(List.of());
        when(repository.findRubricCodesUsedElsewhere(any(), any(), any()))
                .thenReturn(List.of(codeUse("9.9.9", "Vila Aleu")));
        when(budgetItemRepository.findByEnterpriseIdAndCode(any(), any())).thenReturn(Optional.empty());

        assertThat(service.suggestRubric(INVOICE_ID)).isEmpty();
    }

    @Test
    @DisplayName("fatura sem NIF → sem sugestão (não há por onde procurar)")
    void semNif() {
        invoice(null);

        assertThat(service.suggestRubric(INVOICE_ID)).isEmpty();
    }

    @Test
    @DisplayName("fatura sem obra (empresa ou quarentena) → sem sugestão")
    void semObra() {
        ConstructionInvoice inv = invoice("500100200");
        inv.setScope(ConstructionInvoice.Scope.COMPANY);
        inv.setEnterprise(null);

        assertThat(service.suggestRubric(INVOICE_ID)).isEmpty();
    }
}
