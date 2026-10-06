package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSearchFilter;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O lote da fatura (V41): numa obra de vários lotes classifica-se depois de o
 * escolher, e todas as rubricas têm de ser desse lote. Numa obra de um só lote
 * deduz-se, e uma fatura já classificada só ganha o lote que as suas rubricas
 * já têm.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceBudgetLotTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private ConstructionBudgetRepository budgetRepository;
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
    private static final UUID LOT_A = UUID.randomUUID();
    private static final UUID LOT_B = UUID.randomUUID();
    private static final UUID ITEM_OF_A = UUID.randomUUID();
    private static final UUID ITEM_OF_B = UUID.randomUUID();

    private ConstructionInvoice invoice(ConstructionBudget lot) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(INVOICE_ID);
        inv.setDocumentType(ConstructionInvoice.DocumentType.INVOICE);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise enterprise = enterprise();
        inv.setEnterprise(enterprise);
        inv.setInvoiceNumber("FT 2026/50");
        inv.setInvoiceDate(LocalDate.of(2026, 6, 1));
        inv.setTotalAmount(new BigDecimal("500"));
        inv.setBudget(lot);
        return inv;
    }

    private Enterprise enterprise() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        return enterprise;
    }

    private ConstructionBudget lot(UUID id) {
        ConstructionBudget lot = new ConstructionBudget();
        lot.setId(id);
        lot.setEnterprise(enterprise());
        lot.setName("Lote " + id);
        return lot;
    }

    private ConstructionBudgetItem item(UUID id, ConstructionBudget lot) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(id);
        item.setRowKind(BudgetRowKind.ITEM);
        item.setEnterprise(enterprise());
        item.setBudget(lot);
        return item;
    }

    private void givenInvoice(ConstructionInvoice inv, ConstructionBudget... lotsOfObra) {
        when(repository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(budgetRepository.findByEnterpriseIdOrderBySortOrderAscCreatedAtAsc(ENTERPRISE_ID))
                .thenReturn(List.of(lotsOfObra));
        when(budgetItemRepository.findById(ITEM_OF_A)).thenReturn(Optional.of(item(ITEM_OF_A, lot(LOT_A))));
        when(budgetItemRepository.findById(ITEM_OF_B)).thenReturn(Optional.of(item(ITEM_OF_B, lot(LOT_B))));
        when(expenseRepository.save(any(ConstructionExpense.class))).thenAnswer(c -> c.getArgument(0));
    }

    @Test
    @DisplayName("obra de vários lotes: classificar sem lote escolhido recusa-se")
    void multiLotRequiresLotFirst() {
        givenInvoice(invoice(null), lot(LOT_A), lot(LOT_B));

        assertThatThrownBy(() -> service.allocate(INVOICE_ID, ITEM_OF_A))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVOICE_BUDGET_REQUIRED));
    }

    @Test
    @DisplayName("fatura do lote A não aceita rubrica do lote B")
    void rejectsItemOfOtherLot() {
        givenInvoice(invoice(lot(LOT_A)), lot(LOT_A), lot(LOT_B));

        assertThatThrownBy(() -> service.allocate(INVOICE_ID, ITEM_OF_B))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVOICE_BUDGET_OTHER_LOT));
    }

    @Test
    @DisplayName("obra de um só lote: a fatura sem lote ganha o único que existe ao classificar")
    void singleLotDerivesLot() {
        ConstructionBudget only = lot(LOT_A);
        givenInvoice(invoice(null), only);

        service.allocate(INVOICE_ID, ITEM_OF_A);

        assertThat(repositoryInvoice().getBudgetId()).isEqualTo(LOT_A);
    }

    @Test
    @DisplayName("trocar de lote com despesas lançadas recusa-se")
    void cannotChangeLotWhileAllocated() {
        givenInvoice(invoice(lot(LOT_A)), lot(LOT_A), lot(LOT_B));
        ConstructionExpense expense = new ConstructionExpense();
        expense.setBudgetItem(item(ITEM_OF_A, lot(LOT_A)));
        when(expenseRepository.findByInvoiceIdOrderByCreatedAtAsc(INVOICE_ID)).thenReturn(List.of(expense));

        assertThatThrownBy(() -> service.setBudget(INVOICE_ID, LOT_B))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVOICE_BUDGET_HAS_ALLOCATIONS));
    }

    private ConstructionInvoice repositoryInvoice() {
        return repository.findById(INVOICE_ID).orElseThrow();
    }

    @Test
    @DisplayName("filtrar por lote manda o lote à query, e as faturas sem rubrica ficam dentro")
    void searchByLotPassesLotToQuery() {
        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(true), eq(LOT_A), any()))
                .thenReturn(Page.empty());
        InvoiceSearchFilter filter = new InvoiceSearchFilter(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, LOT_A);

        service.search(ENTERPRISE_ID, filter, Pageable.unpaged());

        verify(repository).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(true), eq(LOT_A), any());
    }

    @Test
    @DisplayName("escolher só o lote, sem rubrica nem despesas, grava-o")
    void setsLotWithoutAllocation() {
        ConstructionInvoice inv = invoice(null);
        givenInvoice(inv, lot(LOT_A), lot(LOT_B));

        service.setBudget(INVOICE_ID, LOT_B);

        assertThat(inv.getBudgetId()).isEqualTo(LOT_B);
    }

    @Test
    @DisplayName("o \"por classificar\" de um lote conta só as faturas desse lote")
    void pendingSummaryOfALotCountsOnlyThatLot() {
        when(repository.countPending(ENTERPRISE_ID, true, LOT_A)).thenReturn(2L);
        when(repository.sumPending(ENTERPRISE_ID, true, LOT_A)).thenReturn(new BigDecimal("150"));

        var summary = service.pendingSummary(ENTERPRISE_ID, LOT_A);

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.total()).isEqualByComparingTo("150");
    }
}
