package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSplitLineDTO;
import com.management.managementapi.enterprises.dto.invoice.response.BatchAllocateResultDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Repartir uma fatura por várias rubricas — o que a {@code V32} destrancou.
 *
 * A folha do armazém traz cimento e ferragens; forçá-la toda para uma rubrica
 * era o principal motivo para o gasto por rubrica não bater certo com o Excel da
 * Vilatro. As invariantes que esta fase tem de manter:
 *
 * <ol>
 *   <li>a soma das linhas <b>esgota</b> o total da fatura — o que sobrasse
 *       ficaria fora do orçamento sem ninguém dar por isso;</li>
 *   <li>repartir <b>substitui</b> a repartição anterior, nunca acrescenta a ela;</li>
 *   <li>uma fatura ainda sem total classifica-se na mesma, com as linhas a zero;</li>
 *   <li>o lote é melhor-esforço: uma falha não derruba as outras.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceSplitServiceTest {

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
    private static final UUID CIMENTO = UUID.randomUUID();
    private static final UUID FERRAGENS = UUID.randomUUID();

    private ConstructionInvoice invoice(BigDecimal total) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(INVOICE_ID);
        inv.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise e = new Enterprise();
        e.setId(ENTERPRISE_ID);
        inv.setEnterprise(e);
        inv.setSupplierName("Armazém Central");
        inv.setSupplierNif("500100200");
        inv.setInvoiceNumber("FT 2026/44");
        inv.setInvoiceDate(LocalDate.of(2026, 6, 1));
        inv.setTotalAmount(total);
        return inv;
    }

    /** Uma rubrica desta obra que aceita despesas. */
    private ConstructionBudgetItem item(UUID id) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(id);
        item.setRowKind(BudgetRowKind.ITEM);
        Enterprise e = new Enterprise();
        e.setId(ENTERPRISE_ID);
        item.setEnterprise(e);
        return item;
    }

    private void givenInvoice(ConstructionInvoice inv) {
        when(repository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(budgetItemRepository.findById(CIMENTO)).thenReturn(Optional.of(item(CIMENTO)));
        when(budgetItemRepository.findById(FERRAGENS)).thenReturn(Optional.of(item(FERRAGENS)));
        when(expenseRepository.saveAll(anyList())).thenAnswer(c -> c.getArgument(0));
    }

    private InvoiceSplitLineDTO line(UUID itemId, String amount) {
        return new InvoiceSplitLineDTO(itemId, new BigDecimal(amount));
    }

    @Test
    @DisplayName("repartir 1000 em 700/300 → duas despesas com esses valores")
    void repartePorDuasRubricas() {
        givenInvoice(invoice(new BigDecimal("1000")));

        List<ConstructionExpense> saved = service.split(INVOICE_ID,
                List.of(line(CIMENTO, "700"), line(FERRAGENS, "300")));

        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(ConstructionExpense::getTotalPrice)
                .containsExactly(new BigDecimal("700"), new BigDecimal("300"));
        assertThat(saved).extracting(e -> e.getBudgetItem().getId())
                .containsExactly(CIMENTO, FERRAGENS);
    }

    @Test
    @DisplayName("soma que não esgota o total → INVOICE_028, nada gravado")
    void somaErradaRecusada() {
        givenInvoice(invoice(new BigDecimal("1000")));

        assertThatThrownBy(() -> service.split(INVOICE_ID,
                List.of(line(CIMENTO, "700"), line(FERRAGENS, "200"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_SPLIT_SUM_MISMATCH);

        verify(expenseRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("a mesma rubrica duas vezes → INVOICE_030")
    void rubricaRepetidaRecusada() {
        givenInvoice(invoice(new BigDecimal("1000")));

        assertThatThrownBy(() -> service.split(INVOICE_ID,
                List.of(line(CIMENTO, "700"), line(CIMENTO, "300"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_SPLIT_DUPLICATE_ITEM);
    }

    @Test
    @DisplayName("repartir substitui a repartição anterior, não acrescenta")
    void substituiRepartiçãoAnterior() {
        ConstructionInvoice inv = invoice(new BigDecimal("1000"));
        givenInvoice(inv);

        ConstructionExpense antiga = new ConstructionExpense();
        antiga.setId(UUID.randomUUID());
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(INVOICE_ID))
                .thenReturn(List.of(antiga));

        service.split(INVOICE_ID, List.of(line(CIMENTO, "1000")));

        verify(expenseRepository).deleteAll(List.of(antiga));
    }

    @Test
    @DisplayName("fatura ainda sem total → classifica-se na mesma, linhas a zero (§7)")
    void faturaSemTotalClassificaSeAZero() {
        ConstructionInvoice semTotal = invoice(null);
        givenInvoice(semTotal);

        List<ConstructionExpense> saved = service.split(INVOICE_ID,
                List.of(line(CIMENTO, "700"), line(FERRAGENS, "300")));

        assertThat(saved).extracting(ConstructionExpense::getTotalPrice)
                .containsExactly(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("fatura da empresa ou da quarentena não se reparte — não há orçamento onde")
    void faturaSemObraRecusada() {
        ConstructionInvoice empresa = invoice(new BigDecimal("100"));
        empresa.setScope(ConstructionInvoice.Scope.COMPANY);
        empresa.setEnterprise(null);
        when(repository.findById(INVOICE_ID)).thenReturn(Optional.of(empresa));

        assertThatThrownBy(() -> service.split(INVOICE_ID, List.of(line(CIMENTO, "100"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_SCOPE_NOT_ALLOCATABLE);
    }

    @Test
    @DisplayName("lote: uma fatura já classificada não derruba as outras")
    void loteÉMelhorEsforço() {
        UUID ok1 = UUID.randomUUID();
        UUID jáClassificada = UUID.randomUUID();
        UUID ok2 = UUID.randomUUID();

        for (UUID id : List.of(ok1, jáClassificada, ok2)) {
            ConstructionInvoice inv = invoice(new BigDecimal("50"));
            inv.setId(id);
            when(repository.findById(id)).thenReturn(Optional.of(inv));
        }
        when(budgetItemRepository.findById(CIMENTO)).thenReturn(Optional.of(item(CIMENTO)));
        when(expenseRepository.save(any(ConstructionExpense.class))).thenAnswer(c -> c.getArgument(0));
        // A do meio já tem despesa — o allocate recusa-a com INVOICE_ALREADY_ALLOCATED.
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(jáClassificada))
                .thenReturn(List.of(new ConstructionExpense()));

        BatchAllocateResultDTO result =
                service.batchAllocate(List.of(ok1, jáClassificada, ok2), CIMENTO);

        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).invoiceId()).isEqualTo(jáClassificada);
        assertThat(result.failures().get(0).errorCode())
                .isEqualTo(ErrorCode.INVOICE_ALREADY_ALLOCATED.getCode());
    }
}
