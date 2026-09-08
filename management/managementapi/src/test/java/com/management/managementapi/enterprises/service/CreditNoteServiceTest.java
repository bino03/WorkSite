package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.CreditNoteCreateDTO;
import com.management.managementapi.enterprises.dto.invoice.request.CreditNoteExpenseLineDTO;
import com.management.managementapi.enterprises.dto.invoice.response.CreditNoteSplitPreviewDTO;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
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
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uma nota de crédito não é um documento independente: só existe agarrada a uma
 * fatura já lançada, e o seu único significado é "esta fatura vale menos X". As
 * invariantes que a fase 3 tem de manter:
 *
 * <ol>
 *   <li>a origem tem de ser uma <b>fatura</b>, nunca outra NC (sem NC de NC);</li>
 *   <li>a NC herda o âmbito, a obra e o NIF da origem (NIF diferente → aviso, não bloqueio);</li>
 *   <li>a repartição negativa é <b>proposta</b> na proporção da origem e confirmada — nunca gravada sozinha;</li>
 *   <li>desde a {@code V32} (fase 4) a repartição pode ter N linhas: uma NC sobre uma fatura
 *       repartida 70/30 propõe −70/−30. A soma continua a não ser imposta — a fase 3 decidiu
 *       que uma repartição desalinhada avisa e grava.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditNoteServiceTest {

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

    private static final UUID ORIGIN_ID = UUID.randomUUID();
    private static final UUID ENTERPRISE_ID = UUID.randomUUID();
    private static final UUID BUDGET_ITEM_ID = UUID.randomUUID();

    private ConstructionInvoice origin(BigDecimal total) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(ORIGIN_ID);
        inv.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise e = new Enterprise();
        e.setId(ENTERPRISE_ID);
        inv.setEnterprise(e);
        inv.setSupplierNif("500100200");
        inv.setSupplierName("Betão Liz");
        inv.setInvoiceNumber("FT 2026/10");
        inv.setInvoiceDate(LocalDate.of(2026, 6, 1));
        inv.setTotalAmount(total);
        return inv;
    }

    private ConstructionInvoiceService serviceWithSave() {
        when(repository.save(any(ConstructionInvoice.class))).thenAnswer(c -> {
            ConstructionInvoice i = c.getArgument(0);
            if (i.getId() == null) {
                i.setId(UUID.randomUUID());
            }
            return i;
        });
        when(repository.findCreditNotesFor(any())).thenReturn(List.of());
        when(documentRepository.findByInvoiceIdOrderByUploadedAtAsc(any())).thenReturn(List.of());
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(paymentService.paymentsForInvoice(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(List.of());
        return service;
    }

    private CreditNoteCreateDTO dto(BigDecimal amount, String nif, List<CreditNoteExpenseLineDTO> expenses) {
        return new CreditNoteCreateDTO(amount, "NC 2026/3", null, LocalDate.of(2026, 7, 1),
                nif, "devolução parcial", null, null, expenses);
    }

    @Test
    @DisplayName("NC sobre uma fatura → grava CREDIT_NOTE ligada à origem, com âmbito, obra e NIF herdados")
    void criaNotaDeCredito() {
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin(new BigDecimal("1000"))));

        serviceWithSave().createCreditNote(ORIGIN_ID, dto(new BigDecimal("100"), null, null));

        ArgumentCaptor<ConstructionInvoice> saved = ArgumentCaptor.forClass(ConstructionInvoice.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getDocumentType()).isEqualTo(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        assertThat(saved.getValue().getRelatedInvoiceId()).isEqualTo(ORIGIN_ID);
        assertThat(saved.getValue().getScope()).isEqualTo(ConstructionInvoice.Scope.PROJECT);
        assertThat(saved.getValue().getEnterpriseId()).isEqualTo(ENTERPRISE_ID);
        assertThat(saved.getValue().getSupplierNif()).isEqualTo("500100200");
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("NC sobre outra NC → INVOICE_026, nada gravado")
    void semNcDeNc() {
        ConstructionInvoice origemNc = origin(new BigDecimal("1000"));
        origemNc.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origemNc));

        assertThatThrownBy(() -> serviceWithSave().createCreditNote(ORIGIN_ID, dto(new BigDecimal("50"), null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_CREDIT_NOTE_TARGET_NOT_INVOICE);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("NC sobre uma fatura que não existe → ResourceNotFoundException")
    void origemInexistente() {
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWithSave().createCreditNote(ORIGIN_ID, dto(new BigDecimal("50"), null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("NIF diferente do da origem → grava na mesma (com aviso)")
    void nifDivergenteNaoBloqueia() {
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin(new BigDecimal("1000"))));

        serviceWithSave().createCreditNote(ORIGIN_ID, dto(new BigDecimal("100"), "999888777", null));

        ArgumentCaptor<ConstructionInvoice> saved = ArgumentCaptor.forClass(ConstructionInvoice.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSupplierNif()).isEqualTo("999888777");
    }

    @Test
    @DisplayName("proposta: origem com 1 despesa de 1000 e total 1000, NC de 100 → 1 linha de −100 na rubrica")
    void propostaProporcional() {
        ConstructionInvoice origin = origin(new BigDecimal("1000"));
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin));

        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(BUDGET_ITEM_ID);
        item.setCode("4.2");
        item.setName("Alvenarias");
        ConstructionExpense expense = new ConstructionExpense();
        expense.setBudgetItem(item);
        expense.setTotalPrice(new BigDecimal("1000"));
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(ORIGIN_ID)).thenReturn(List.of(expense));

        CreditNoteSplitPreviewDTO preview = service.previewCreditNoteSplit(ORIGIN_ID, new BigDecimal("100"));

        assertThat(preview.originAllocated()).isTrue();
        assertThat(preview.lines()).hasSize(1);
        assertThat(preview.lines().get(0).budgetItemId()).isEqualTo(BUDGET_ITEM_ID);
        assertThat(preview.lines().get(0).amount()).isEqualByComparingTo("-100");
        assertThat(preview.total()).isEqualByComparingTo("-100");
    }

    @Test
    @DisplayName("proposta: origem sem despesa → originAllocated=false e nenhuma linha")
    void propostaSemDespesa() {
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin(new BigDecimal("1000"))));
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(ORIGIN_ID)).thenReturn(List.of());

        CreditNoteSplitPreviewDTO preview = service.previewCreditNoteSplit(ORIGIN_ID, new BigDecimal("100"));

        assertThat(preview.originAllocated()).isFalse();
        assertThat(preview.lines()).isEmpty();
    }

    @Test
    @DisplayName("criar com 2 linhas de repartição → grava as duas despesas negativas (V32)")
    void repartiçãoPorDuasRubricas() {
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin(new BigDecimal("1000"))));

        UUID secondItemId = UUID.randomUUID();
        when(budgetItemRepository.findById(BUDGET_ITEM_ID))
                .thenReturn(Optional.of(itemOfEnterprise(BUDGET_ITEM_ID)));
        when(budgetItemRepository.findById(secondItemId))
                .thenReturn(Optional.of(itemOfEnterprise(secondItemId)));

        List<CreditNoteExpenseLineDTO> lines = List.of(
                new CreditNoteExpenseLineDTO(BUDGET_ITEM_ID, new BigDecimal("70")),
                new CreditNoteExpenseLineDTO(secondItemId, new BigDecimal("30")));

        serviceWithSave().createCreditNote(ORIGIN_ID, dto(new BigDecimal("100"), null, lines));

        ArgumentCaptor<ConstructionExpense> saved = ArgumentCaptor.forClass(ConstructionExpense.class);
        verify(expenseRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ConstructionExpense::getTotalPrice)
                .containsExactly(new BigDecimal("-70"), new BigDecimal("-30"));
    }

    @Test
    @DisplayName("NC totalmente repartida → allocationStatus COMPLETE, nada por repartir")
    void repartiçãoDaNcNaoFicaPendente() {
        ConstructionInvoice nc = origin(new BigDecimal("100"));
        nc.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        nc.setRelatedInvoiceId(UUID.randomUUID());

        // As despesas de uma NC são negativas: −100 reparte 100 por inteiro.
        ConstructionExpense negativa = new ConstructionExpense();
        negativa.setId(UUID.randomUUID());
        negativa.setTotalPrice(new BigDecimal("-100"));
        negativa.setBudgetItem(itemOfEnterprise(BUDGET_ITEM_ID));
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(ORIGIN_ID))
                .thenReturn(List.of(negativa));
        when(repository.findCreditNotesFor(any())).thenReturn(List.of());
        when(documentRepository.findByInvoiceIdOrderByUploadedAtAsc(any())).thenReturn(List.of());
        when(paymentService.paymentsForInvoice(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(List.of());

        var dto = service.toResponseDTO(nc, false);

        // `100 − (−100) = 200` era o que dava antes: a NC ficava eternamente
        // "por repartir 200,00 €" no ecrã, já estando completa.
        assertThat(dto.unallocatedAmount()).isEqualByComparingTo("0");
        assertThat(dto.allocationStatus()).isEqualTo("COMPLETE");
    }

    /** Uma rubrica qualquer da obra da fatura de origem — o serviço só valida a obra. */
    private ConstructionBudgetItem itemOfEnterprise(UUID itemId) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(itemId);
        Enterprise enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        item.setEnterprise(enterprise);
        return item;
    }

    @Test
    @DisplayName("criar com 1 linha confirmada → grava a despesa negativa na rubrica")
    void gravaDespesaNegativa() {
        when(repository.findById(ORIGIN_ID)).thenReturn(Optional.of(origin(new BigDecimal("1000"))));

        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(BUDGET_ITEM_ID);
        Enterprise e = new Enterprise();
        e.setId(ENTERPRISE_ID);
        item.setEnterprise(e);
        when(budgetItemRepository.findById(BUDGET_ITEM_ID)).thenReturn(Optional.of(item));

        List<CreditNoteExpenseLineDTO> lines = List.of(
                new CreditNoteExpenseLineDTO(BUDGET_ITEM_ID, new BigDecimal("100")));

        serviceWithSave().createCreditNote(ORIGIN_ID, dto(new BigDecimal("100"), null, lines));

        ArgumentCaptor<ConstructionExpense> expense = ArgumentCaptor.forClass(ConstructionExpense.class);
        verify(expenseRepository).save(expense.capture());
        assertThat(expense.getValue().getTotalPrice()).isEqualByComparingTo("-100");
        assertThat(expense.getValue().getBudgetItem()).isEqualTo(item);
    }
}
