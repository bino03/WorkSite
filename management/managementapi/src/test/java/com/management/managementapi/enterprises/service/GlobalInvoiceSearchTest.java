package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.request.GlobalInvoiceSearchFilter;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceSearchResultDTO;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pesquisa global de faturas (o modal Ctrl+K, 2026-10-22): o texto chega à
 * query aparado, um texto curto não chega lá, e um texto que se lê como valor
 * vai também como valor. Desde 2026-11-10 aceita filtros (obra, âmbito,
 * fornecedor, pagamento), e com um filtro o texto é opcional.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalInvoiceSearchTest {

    private static final UUID NO_ENTERPRISE = new UUID(0L, 0L);

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionBudgetRepository budgetRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    private List<InvoiceSearchResultDTO> search(String q, int limit) {
        return service.searchEverywhere(GlobalInvoiceSearchFilter.text(q), limit);
    }

    private void stubSearch(List<ConstructionInvoice> result) {
        when(repository.searchEverywhere(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(result);
    }

    @Test
    @DisplayName("sem filtro, menos de 2 caracteres não pesquisa")
    void shortTextReturnsNothing() {
        assertThat(search(" a ", 20)).isEmpty();
        assertThat(search(null, 20)).isEmpty();
        verify(repository, never()).searchEverywhere(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("o texto vai aparado, sem valor quando não é número, com o limite pedido")
    void textIsTrimmedAndLimited() {
        stubSearch(List.of());

        search("  brivel ", 7);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchEverywhere(eq("brivel"), isNull(), eq(false), eq(NO_ENTERPRISE),
                isNull(), isNull(), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
    }

    @Test
    @DisplayName("um valor à portuguesa vai também como valor")
    void amountTextIsAlsoSearchedAsAmount() {
        search("1 234,56 €", 20);

        verify(repository).searchEverywhere(eq("1 234,56 €"), eq(new BigDecimal("1234.56")), anyBoolean(),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("só com um fornecedor, pesquisa sem texto")
    void filterAloneSearchesWithoutText() {
        stubSearch(List.of());

        service.searchEverywhere(new GlobalInvoiceSearchFilter(null, null, null, " 502693134 ", null), 50);

        verify(repository).searchEverywhere(isNull(), isNull(), eq(false), eq(NO_ENTERPRISE),
                isNull(), eq("502693134"), isNull(), any());
    }

    @Test
    @DisplayName("os filtros chegam à query: obra com flag, âmbito e pagamento como texto em maiúsculas")
    void filtersReachTheQuery() {
        UUID enterpriseId = UUID.randomUUID();
        stubSearch(List.of());

        service.searchEverywhere(new GlobalInvoiceSearchFilter("x", enterpriseId, null, null, "outstanding"), 20);
        service.searchEverywhere(new GlobalInvoiceSearchFilter("", null, ConstructionInvoice.Scope.COMPANY, null, "PAID"), 20);

        verify(repository).searchEverywhere(eq("x"), isNull(), eq(true), eq(enterpriseId),
                isNull(), isNull(), eq("OUTSTANDING"), any());
        verify(repository).searchEverywhere(isNull(), isNull(), eq(false), eq(NO_ENTERPRISE),
                eq("COMPANY"), isNull(), eq("PAID"), any());
    }

    @Test
    @DisplayName("cada linha traz o estado de pagamento, descontando as notas de crédito")
    void mapsPaymentStatus() {
        ConstructionInvoice unpaid = invoice(ConstructionInvoice.Scope.COMPANY, null);
        ConstructionInvoice paidWithCredit = invoice(ConstructionInvoice.Scope.COMPANY, null);
        ConstructionInvoice partial = invoice(ConstructionInvoice.Scope.COMPANY, null);
        stubSearch(List.of(unpaid, paidWithCredit, partial));

        ConstructionInvoice creditNote = invoice(ConstructionInvoice.Scope.COMPANY, null);
        creditNote.setRelatedInvoiceId(paidWithCredit.getId());
        creditNote.setTotalAmount(new BigDecimal("4.00"));
        when(repository.findCreditNotesForAll(any())).thenReturn(List.of(creditNote));
        // Fora do when(): criar um mock dentro do thenReturn deixa o stubbing a meio.
        InvoicePaymentSummaryDTO six = payment("6.00");
        InvoicePaymentSummaryDTO three = payment("3.00");
        when(paymentService.paymentsForInvoices(any(), eq(false))).thenReturn(Map.of(
                paidWithCredit.getId(), List.of(six),
                partial.getId(), List.of(three)));

        List<InvoiceSearchResultDTO> rows = service.searchEverywhere(
                new GlobalInvoiceSearchFilter(null, null, ConstructionInvoice.Scope.COMPANY, null, null), 50);

        assertThat(rows).extracting(InvoiceSearchResultDTO::paymentStatus)
                .containsExactly("UNPAID", "PAID", "PARTIAL");
    }

    @Test
    @DisplayName("formatos de valor aceites e recusados")
    void parsesAmounts() {
        assertThat(ConstructionInvoiceService.parseSearchAmount("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConstructionInvoiceService.parseSearchAmount("1234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConstructionInvoiceService.parseSearchAmount("1234.56")).isEqualByComparingTo("1234.56");
        assertThat(ConstructionInvoiceService.parseSearchAmount("24,9")).isEqualByComparingTo("24.90");
        assertThat(ConstructionInvoiceService.parseSearchAmount("1222")).isEqualByComparingTo("1222");
        assertThat(ConstructionInvoiceService.parseSearchAmount("20263/1222")).isNull();
        assertThat(ConstructionInvoiceService.parseSearchAmount("FT 1222")).isNull();
        assertThat(ConstructionInvoiceService.parseSearchAmount("1,2345")).isNull();
    }

    @Test
    @DisplayName("a linha traz o nome da obra, e nada de obra na empresa")
    void mapsEnterpriseName() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(UUID.randomUUID());
        enterprise.setName("Vila Petrus");

        ConstructionInvoice project = invoice(ConstructionInvoice.Scope.PROJECT, enterprise);
        ConstructionInvoice company = invoice(ConstructionInvoice.Scope.COMPANY, null);
        company.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        stubSearch(List.of(project, company));

        List<InvoiceSearchResultDTO> rows = search("ft", 20);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).enterpriseId()).isEqualTo(enterprise.getId());
        assertThat(rows.get(0).enterpriseName()).isEqualTo("Vila Petrus");
        assertThat(rows.get(0).scope()).isEqualTo("PROJECT");
        assertThat(rows.get(1).enterpriseName()).isNull();
        assertThat(rows.get(1).documentType()).isEqualTo("CREDIT_NOTE");
    }

    @Test
    @DisplayName("o lote só aparece numa obra com mais de um lote vivo")
    void showsBudgetNameOnlyWhenEnterpriseHasSeveral() {
        Enterprise villa = new Enterprise();
        villa.setId(UUID.randomUUID());
        villa.setName("Vila Aleu");
        Enterprise single = new Enterprise();
        single.setId(UUID.randomUUID());
        single.setName("Vila Petrus");

        ConstructionBudget lotA = budget(villa, "Lote A");
        ConstructionBudget lotB = budget(villa, "Lote B");
        ConstructionBudget only = budget(single, "Orçamento");
        when(budgetRepository.findByEnterpriseIdInAndDeletedAtIsNullOrderBySortOrderAsc(any()))
                .thenReturn(List.of(lotA, lotB, only));

        ConstructionInvoice inLot = invoice(ConstructionInvoice.Scope.PROJECT, villa);
        inLot.setBudget(lotB);
        ConstructionInvoice noLot = invoice(ConstructionInvoice.Scope.PROJECT, villa);
        ConstructionInvoice inSingle = invoice(ConstructionInvoice.Scope.PROJECT, single);
        inSingle.setBudget(only);
        stubSearch(List.of(inLot, noLot, inSingle));

        List<InvoiceSearchResultDTO> rows = search("ft", 20);

        assertThat(rows.get(0).budgetName()).isEqualTo("Lote B");
        assertThat(rows.get(1).budgetName()).isNull();
        assertThat(rows.get(2).budgetName()).isNull();
    }

    private static InvoicePaymentSummaryDTO payment(String amount) {
        InvoicePaymentSummaryDTO payment = mock(InvoicePaymentSummaryDTO.class);
        when(payment.amountOnThisInvoice()).thenReturn(new BigDecimal(amount));
        return payment;
    }

    private static ConstructionBudget budget(Enterprise enterprise, String name) {
        ConstructionBudget budget = new ConstructionBudget();
        budget.setEnterprise(enterprise);
        budget.setName(name);
        return budget;
    }

    private static ConstructionInvoice invoice(ConstructionInvoice.Scope scope, Enterprise enterprise) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setScope(scope);
        invoice.setEnterprise(enterprise);
        invoice.setInvoiceNumber("FT 1");
        invoice.setInvoiceDate(LocalDate.of(2026, 10, 1));
        invoice.setTotalAmount(new BigDecimal("10.00"));
        return invoice;
    }
}
