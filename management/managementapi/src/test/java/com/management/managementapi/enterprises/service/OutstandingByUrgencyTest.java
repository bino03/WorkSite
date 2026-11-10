package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.request.OutstandingFilter;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.OutstandingByUrgencyDTO;
import com.management.managementapi.enterprises.dto.invoice.response.OutstandingSupplierDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * "O que pagar primeiro": as faturas por liquidar de todas as obras, com as
 * vencidas à frente, depois as que vencem mais cedo, e as sem vencimento no fim.
 */
@ExtendWith(MockitoExtension.class)
class OutstandingByUrgencyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private static final String NIF_30_DAYS = "502693134";

    @Mock private ConstructionInvoiceRepository repository;
    @Mock private ConstructionInvoiceDocumentRepository documentRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private ConstructionBudgetItemRepository budgetItemRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseStorageService storageService;
    @Mock private SignedUrlService signedUrls;
    @Mock private PaymentService paymentService;

    @InjectMocks private ConstructionInvoiceService service;

    private Enterprise petrus;
    private ConstructionInvoice overdue;
    private ConstructionInvoice dueSoon;
    private ConstructionInvoice dueLaterByHand;
    private ConstructionInvoice noDueDateOlder;
    private ConstructionInvoice noDueDateNewer;

    @BeforeEach
    void setUp() {
        petrus = new Enterprise();
        petrus.setId(UUID.randomUUID());
        petrus.setName("Vila Petrus");

        Supplier supplier = new Supplier();
        supplier.setNif(NIF_30_DAYS);
        supplier.setName("Brivel");
        supplier.setPaymentTermDays(30);

        // 1 set + 30 dias = 1 out → já passou
        overdue = invoice("100", NIF_30_DAYS, LocalDate.of(2026, 9, 1));
        overdue.setEnterprise(petrus);
        // 15 set + 30 dias = 15 out
        dueSoon = invoice("200", NIF_30_DAYS, LocalDate.of(2026, 9, 15));
        // sem NIF (como as do Excel), mas com vencimento escrito à mão
        dueLaterByHand = invoice("400", null, LocalDate.of(2026, 8, 1));
        dueLaterByHand.setDueDateOverride(LocalDate.of(2026, 10, 20));
        noDueDateOlder = invoice("800", null, LocalDate.of(2026, 7, 1));
        noDueDateNewer = invoice("1600", null, LocalDate.of(2026, 9, 30));

        // A base de dados devolve-as sem ordem nenhuma.
        when(repository.findOutstandingExcludingScope(ConstructionInvoice.Scope.UNIDENTIFIED))
                .thenReturn(List.of(noDueDateNewer, dueLaterByHand, overdue, noDueDateOlder, dueSoon));
        when(supplierRepository.findAll()).thenReturn(List.of(supplier));
    }

    @Test
    @DisplayName("vencidas primeiro, depois as mais próximas, e as sem vencimento no fim, da mais antiga para a mais recente")
    void ordersByUrgency() {
        OutstandingByUrgencyDTO result = service.outstandingByUrgency(OutstandingFilter.NONE, 0, 20, TODAY);

        assertThat(result.invoices()).extracting(ConstructionInvoiceResponseDTO::id).containsExactly(
                overdue.getId(), dueSoon.getId(), dueLaterByHand.getId(),
                noDueDateOlder.getId(), noDueDateNewer.getId());
    }

    @Test
    @DisplayName("o resumo é da lista inteira: total, vencidas e sem vencimento")
    void summaryCoversTheWholeList() {
        OutstandingByUrgencyDTO result = service.outstandingByUrgency(OutstandingFilter.NONE, 0, 2, TODAY);

        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.total()).isEqualByComparingTo("3100");
        assertThat(result.overdueCount()).isEqualTo(1);
        assertThat(result.overdueTotal()).isEqualByComparingTo("100");
        assertThat(result.withoutDueDateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("uma fatura que vence hoje ainda não está vencida")
    void dueTodayIsNotOverdue() {
        OutstandingByUrgencyDTO result = service.outstandingByUrgency(OutstandingFilter.NONE, 0, 20, LocalDate.of(2026, 10, 1));

        assertThat(result.overdueCount()).isZero();
    }

    @Test
    @DisplayName("a paginação corta a lista já ordenada, e uma página para lá do fim vem vazia")
    void paginatesTheOrderedList() {
        OutstandingByUrgencyDTO second = service.outstandingByUrgency(OutstandingFilter.NONE, 1, 2, TODAY);

        assertThat(second.invoices()).extracting(ConstructionInvoiceResponseDTO::id)
                .containsExactly(dueLaterByHand.getId(), noDueDateOlder.getId());
        assertThat(service.outstandingByUrgency(OutstandingFilter.NONE, 9, 2, TODAY).invoices()).isEmpty();
    }

    @Test
    @DisplayName("traz o nome das obras da página; uma despesa da empresa não tem obra")
    void carriesEnterpriseNames() {
        OutstandingByUrgencyDTO result = service.outstandingByUrgency(OutstandingFilter.NONE, 0, 20, TODAY);

        assertThat(result.enterpriseNames()).containsOnlyKeys(petrus.getId());
        assertThat(result.enterpriseNames().get(petrus.getId())).isEqualTo("Vila Petrus");
    }

    @Test
    @DisplayName("filtrado por fornecedor, os totais do topo são só dele")
    void supplierFilterNarrowsTheTotals() {
        OutstandingByUrgencyDTO result = service.outstandingByUrgency(
                new OutstandingFilter(null, false, " " + NIF_30_DAYS + " ", null, null, null), 0, 20, TODAY);

        assertThat(result.invoices()).extracting(ConstructionInvoiceResponseDTO::id)
                .containsExactly(overdue.getId(), dueSoon.getId());
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.total()).isEqualByComparingTo("300");
        assertThat(result.overdueCount()).isEqualTo(1);
        assertThat(result.overdueTotal()).isEqualByComparingTo("100");
        assertThat(result.withoutDueDateCount()).isZero();
    }

    @Test
    @DisplayName("as faixas de vencimento são cumulativas e não incluem as vencidas")
    void dueBuckets() {
        assertThat(idsFor(dueOnly(OutstandingFilter.DueBucket.OVERDUE))).containsExactly(overdue.getId());
        // 15 out e 20 out: hoje + 7 = 20 out, inclusive.
        assertThat(idsFor(dueOnly(OutstandingFilter.DueBucket.WEEK)))
                .containsExactly(dueSoon.getId(), dueLaterByHand.getId());
        assertThat(idsFor(dueOnly(OutstandingFilter.DueBucket.MONTH)))
                .containsExactly(dueSoon.getId(), dueLaterByHand.getId());

        OutstandingByUrgencyDTO none = service.outstandingByUrgency(dueOnly(OutstandingFilter.DueBucket.NONE), 0, 20, TODAY);
        assertThat(none.invoices()).extracting(ConstructionInvoiceResponseDTO::id)
                .containsExactly(noDueDateOlder.getId(), noDueDateNewer.getId());
        assertThat(none.total()).isEqualByComparingTo("2400");
        assertThat(none.overdueCount()).isZero();
        assertThat(none.withoutDueDateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("por obra fica só a dessa obra; \"despesas da empresa\" deixa as obras de fora")
    void enterpriseAndCompanyFilters() {
        overdue.setScope(ConstructionInvoice.Scope.PROJECT);

        assertThat(idsFor(new OutstandingFilter(petrus.getId(), false, null, null, null, null)))
                .containsExactly(overdue.getId());
        assertThat(idsFor(new OutstandingFilter(null, true, null, null, null, null)))
                .doesNotContain(overdue.getId())
                .hasSize(4);
    }

    @Test
    @DisplayName("aprovada para pagamento e enviada ao contabilista filtram nos dois sentidos")
    void approvalAndAccountantFilters() {
        dueSoon.setApprovedForPayment(true);
        noDueDateOlder.setSentToAccountant(true);

        assertThat(idsFor(new OutstandingFilter(null, false, null, null, true, null))).containsExactly(dueSoon.getId());
        assertThat(idsFor(new OutstandingFilter(null, false, null, null, false, null))).hasSize(4);
        assertThat(idsFor(new OutstandingFilter(null, false, null, null, null, true))).containsExactly(noDueDateOlder.getId());
        assertThat(idsFor(new OutstandingFilter(null, false, null, null, false, false))).hasSize(3);
    }

    @Test
    @DisplayName("as opções de fornecedor são só quem tem faturas por liquidar, e não encolhem ao escolher um")
    void suppliersAreOnlyThoseWithOutstandingInvoices() {
        noDueDateOlder.setSupplierNif("509299466");
        noDueDateOlder.setSupplierName("Paulo Dinis");

        OutstandingByUrgencyDTO all = service.outstandingByUrgency(OutstandingFilter.NONE, 0, 20, TODAY);
        // Brivel vem do catálogo; Paulo Dinis, sem ficha, do nome na fatura; as sem NIF não dão opção.
        assertThat(all.suppliers()).containsExactly(
                new OutstandingSupplierDTO(NIF_30_DAYS, "Brivel"),
                new OutstandingSupplierDTO("509299466", "Paulo Dinis"));

        OutstandingByUrgencyDTO oneSupplier = service.outstandingByUrgency(
                new OutstandingFilter(null, false, "509299466", null, null, null), 0, 20, TODAY);
        assertThat(oneSupplier.suppliers()).hasSize(2);
        assertThat(oneSupplier.totalElements()).isEqualTo(1);

        // Os outros filtros, esses, estreitam as opções: nas vencidas só há a da Brivel.
        assertThat(service.outstandingByUrgency(dueOnly(OutstandingFilter.DueBucket.OVERDUE), 0, 20, TODAY).suppliers())
                .extracting(OutstandingSupplierDTO::nif)
                .containsExactly(NIF_30_DAYS);
    }

    private static OutstandingFilter dueOnly(OutstandingFilter.DueBucket bucket) {
        return new OutstandingFilter(null, false, null, bucket, null, null);
    }

    private List<UUID> idsFor(OutstandingFilter filter) {
        return service.outstandingByUrgency(filter, 0, 20, TODAY).invoices().stream()
                .map(ConstructionInvoiceResponseDTO::id)
                .toList();
    }

    private static ConstructionInvoice invoice(String total, String supplierNif, LocalDate invoiceDate) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setScope(ConstructionInvoice.Scope.COMPANY);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setSupplierNif(supplierNif);
        invoice.setInvoiceDate(invoiceDate);
        return invoice;
    }
}
