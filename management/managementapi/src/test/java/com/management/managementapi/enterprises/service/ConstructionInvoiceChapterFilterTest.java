package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
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
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Filtro "classificadas ao capítulo" (ToDo, Construção & Despesas): a rubrica de
 * uma despesa ainda tem filhas {@code ITEM} — sinal de que a fatura devia ter ido
 * mais fundo na árvore. Mesma regra do ecrã "Classificar" ({@code BudgetItemSearchResultDTO}).
 */
@ExtendWith(MockitoExtension.class)
class ConstructionInvoiceChapterFilterTest {

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    @Test
    @DisplayName("passa atChapter e BudgetRowKind.ITEM ao repositório")
    void passesAtChapterThroughToRepository() {
        UUID enterpriseId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(enterpriseId, null, null, null, null, true, null, null, null, pageable);

        ArgumentCaptor<Boolean> atChapterCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<BudgetRowKind> rowKindCaptor = ArgumentCaptor.forClass(BudgetRowKind.class);
        verify(repository).search(eq(enterpriseId), any(), any(), any(), any(),
                atChapterCaptor.capture(), rowKindCaptor.capture(), any(), any(), any(), eq(pageable));

        assertThat(atChapterCaptor.getValue()).isTrue();
        assertThat(rowKindCaptor.getValue()).isEqualTo(BudgetRowKind.ITEM);
    }

    @Test
    @DisplayName("a linha de repartição vem marcada 'chapter' quando a rubrica ainda tem filhas ITEM")
    void marksAllocationAsChapterWhenBudgetItemHasItemChildren() {
        UUID enterpriseId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID budgetItemId = UUID.randomUUID();

        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);

        ConstructionBudgetItem budgetItem = new ConstructionBudgetItem();
        budgetItem.setId(budgetItemId);
        budgetItem.setCode("4.2");
        budgetItem.setName("Estrutura");

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(invoiceId);
        invoice.setEnterprise(enterprise);
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setInvoiceDate(LocalDate.of(2026, 1, 15));

        ConstructionExpense expense = new ConstructionExpense();
        expense.setId(UUID.randomUUID());
        expense.setInvoice(invoice);
        expense.setBudgetItem(budgetItem);
        expense.setTotalPrice(new BigDecimal("100.00"));
        expense.setExpenseDate(LocalDate.of(2026, 1, 15));

        Pageable pageable = PageRequest.of(0, 20);
        Page<ConstructionInvoice> page = new PageImpl<>(List.of(invoice));

        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);
        when(expenseRepository.findByInvoiceIdIn(List.of(invoiceId))).thenReturn(List.of(expense));
        when(documentRepository.findByInvoiceIdInOrderByUploadedAtAsc(List.of(invoiceId))).thenReturn(List.of());
        when(paymentService.paymentsForInvoices(eq(List.of(invoiceId)), eq(false))).thenReturn(Map.of());
        when(repository.findCreditNotesForAll(List.of(invoiceId))).thenReturn(List.of());
        when(budgetItemRepository.existsByParentIdAndRowKind(budgetItemId, BudgetRowKind.ITEM)).thenReturn(true);

        Page<ConstructionInvoiceResponseDTO> result = service.search(
                enterpriseId, null, null, null, null, true, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).allocations()).hasSize(1);
        assertThat(result.getContent().get(0).allocations().get(0).chapter()).isTrue();
    }
}
