package com.management.managementapi.enterprises.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.managementapi.dto.activity.ActivityLogCreateDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceTransferDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceTransferResultDTO;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ActivityLogRepository;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Transferir uma fatura de âmbito/obra (fase 5). As invariantes:
 *
 * <ol>
 *   <li>a repartição antiga vai para o {@code activity_log} <b>antes</b> de as
 *       despesas serem apagadas;</li>
 *   <li>razão obrigatória;</li>
 *   <li>para {@code COMPANY}/{@code UNIDENTIFIED} a obra é limpa (check
 *       {@code ck_invoice_scope_enterprise});</li>
 *   <li>obra de teste como destino → recusa;</li>
 *   <li>as notas de crédito ligadas seguem a fatura, e as despesas delas também
 *       são apagadas.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceTransferServiceTest {

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
    @Mock private ActivityLogService activityLogService;
    @Mock private ActivityLogRepository activityLogRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ConstructionInvoiceService service;

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID FROM_ENTERPRISE = UUID.randomUUID();
    private static final UUID TO_ENTERPRISE = UUID.randomUUID();

    @BeforeEach
    void wiring() {
        when(authContext.currentProfileId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(authContext.currentUserName()).thenReturn(Optional.of("Rita"));
        when(repository.save(any(ConstructionInvoice.class))).thenAnswer(c -> c.getArgument(0));
        when(repository.findCreditNotesFor(any())).thenReturn(List.of());
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(documentRepository.findByInvoiceIdOrderByUploadedAtAsc(any())).thenReturn(List.of());
        when(paymentService.paymentsForInvoice(any(), anyBoolean())).thenReturn(List.of());
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
    }

    private ConstructionInvoice invoiceOnProject() {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(INVOICE_ID);
        inv.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        inv.setDocumentStatus(ConstructionInvoice.DocumentStatus.MISSING);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise from = new Enterprise();
        from.setId(FROM_ENTERPRISE);
        from.setName("Vila Petrus");
        inv.setEnterprise(from);
        inv.setSupplierName("Casa Dolores");
        inv.setInvoiceNumber("FT 2026/9");
        inv.setInvoiceDate(LocalDate.of(2026, 5, 1));
        inv.setTotalAmount(new BigDecimal("1000"));
        when(repository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(enterpriseRepository.findById(FROM_ENTERPRISE)).thenReturn(Optional.of(from));
        return inv;
    }

    private Enterprise target(boolean isTest) {
        Enterprise e = new Enterprise();
        e.setId(TO_ENTERPRISE);
        e.setName("Vila Aleu");
        e.setIsTest(isTest);
        when(enterpriseRepository.findById(TO_ENTERPRISE)).thenReturn(Optional.of(e));
        return e;
    }

    private static ConstructionExpense expense() {
        ConstructionExpense e = new ConstructionExpense();
        e.setId(UUID.randomUUID());
        e.setTotalPrice(new BigDecimal("1000"));
        return e;
    }

    @Test
    @DisplayName("obra → obra: apaga as despesas, muda o âmbito, escreve o log")
    void obraParaObra() {
        ConstructionInvoice inv = invoiceOnProject();
        target(false);
        ConstructionExpense antiga = expense();
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(INVOICE_ID)).thenReturn(List.of(antiga));

        InvoiceTransferResultDTO result = service.transfer(INVOICE_ID,
                new InvoiceTransferDTO("PROJECT", TO_ENTERPRISE, "fatura era da Aleu, não da Petrus"));

        verify(expenseRepository).deleteAll(List.of(antiga));
        assertThat(inv.getScope()).isEqualTo(ConstructionInvoice.Scope.PROJECT);
        assertThat(inv.getEnterpriseId()).isEqualTo(TO_ENTERPRISE);

        ArgumentCaptor<ActivityLogCreateDTO> log = ArgumentCaptor.forClass(ActivityLogCreateDTO.class);
        verify(activityLogService).createActivity(log.capture());
        assertThat(log.getValue().activityType()).isEqualTo(ActivityType.TRANSFER);
        assertThat(log.getValue().metadata()).contains("fatura era da Aleu", "Vila Petrus", "Vila Aleu");
        assertThat(result.suggestIncident()).isTrue(); // tinha repartição
    }

    @Test
    @DisplayName("o log é escrito ANTES de as despesas serem apagadas")
    void logAntesDoDelete() {
        invoiceOnProject();
        target(false);
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(INVOICE_ID)).thenReturn(List.of(expense()));

        service.transfer(INVOICE_ID, new InvoiceTransferDTO("COMPANY", null, "passa a despesa da empresa"));

        InOrder order = inOrder(activityLogService, expenseRepository);
        order.verify(activityLogService).createActivity(any());
        order.verify(expenseRepository).deleteAll(anyList());
    }

    @Test
    @DisplayName("sem razão → recusada, nada apagado")
    void semRazao() {
        invoiceOnProject();
        target(false);

        assertThatThrownBy(() -> service.transfer(INVOICE_ID,
                new InvoiceTransferDTO("PROJECT", TO_ENTERPRISE, "   ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(expenseRepository, never()).deleteAll(anyList());
        verify(activityLogService, never()).createActivity(any());
    }

    @Test
    @DisplayName("destino UNIDENTIFIED limpa a obra")
    void paraQuarentenaLimpaObra() {
        ConstructionInvoice inv = invoiceOnProject();

        service.transfer(INVOICE_ID, new InvoiceTransferDTO("UNIDENTIFIED", null, "não se sabe de quem é"));

        assertThat(inv.getScope()).isEqualTo(ConstructionInvoice.Scope.UNIDENTIFIED);
        assertThat(inv.getEnterprise()).isNull();
    }

    @Test
    @DisplayName("destino é obra de teste → INVOICE_031, nada apagado")
    void destinoDeTeste() {
        invoiceOnProject();
        target(true);
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(INVOICE_ID)).thenReturn(List.of(expense()));

        assertThatThrownBy(() -> service.transfer(INVOICE_ID,
                new InvoiceTransferDTO("PROJECT", TO_ENTERPRISE, "engano")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_TRANSFER_TEST_TARGET);

        verify(expenseRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("destino == origem → INVOICE_032")
    void mesmoDestino() {
        invoiceOnProject();
        Enterprise same = new Enterprise();
        same.setId(FROM_ENTERPRISE);
        when(enterpriseRepository.findById(FROM_ENTERPRISE)).thenReturn(Optional.of(same));

        assertThatThrownBy(() -> service.transfer(INVOICE_ID,
                new InvoiceTransferDTO("PROJECT", FROM_ENTERPRISE, "sem efeito")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_TRANSFER_SAME_TARGET);
    }

    @Test
    @DisplayName("nota de crédito diretamente → INVOICE_033 (segue a fatura, não sozinha)")
    void notaDeCreditoDireta() {
        ConstructionInvoice nc = invoiceOnProject();
        nc.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        target(false);

        assertThatThrownBy(() -> service.transfer(INVOICE_ID,
                new InvoiceTransferDTO("PROJECT", TO_ENTERPRISE, "não")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_TRANSFER_CREDIT_NOTE);
    }

    @Test
    @DisplayName("as notas de crédito ligadas seguem a fatura")
    void notasDeCreditoSeguemAFatura() {
        invoiceOnProject();
        target(false);

        ConstructionInvoice nc = new ConstructionInvoice();
        nc.setId(UUID.randomUUID());
        nc.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        nc.setDocumentStatus(ConstructionInvoice.DocumentStatus.MISSING);
        nc.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise from = new Enterprise();
        from.setId(FROM_ENTERPRISE);
        nc.setEnterprise(from);
        nc.setTotalAmount(new BigDecimal("100"));
        when(repository.findCreditNotesFor(INVOICE_ID)).thenReturn(List.of(nc));

        ConstructionExpense ncExpense = expense();
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(nc.getId())).thenReturn(List.of(ncExpense));

        service.transfer(INVOICE_ID, new InvoiceTransferDTO("PROJECT", TO_ENTERPRISE, "obra trocada"));

        assertThat(nc.getEnterpriseId()).isEqualTo(TO_ENTERPRISE);
        verify(expenseRepository).deleteAll(List.of(ncExpense));
        verify(repository).save(nc);
    }
}
