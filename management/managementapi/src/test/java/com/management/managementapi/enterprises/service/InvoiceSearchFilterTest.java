package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSearchFilter;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pesquisa avançada da lista de faturas (2026-09-21): o filtro chega inteiro
 * à query, e a rubrica pedida vira a sua sub-árvore — "tudo o que foi para a
 * 4.2" tem de apanhar a 4.2.1 e a 4.2.2.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceSearchFilterTest {

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

    private final UUID enterpriseId = UUID.randomUUID();
    private final Pageable pageable = PageRequest.of(0, 20);
    private ConstructionBudgetItem c4, c42, c421, c422, c43, deleted;

    @BeforeEach
    void setUp() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(enterpriseId);
        c4 = item(enterprise, null, "4");
        c42 = item(enterprise, c4, "4.2");
        c421 = item(enterprise, c42, "4.2.1");
        c422 = item(enterprise, c42, "4.2.2");
        c43 = item(enterprise, c4, "4.3");
        deleted = item(enterprise, c42, "4.2.9");
        deleted.setDeletedAt(java.time.OffsetDateTime.now());

        when(budgetItemRepository.findTreeByEnterpriseId(enterpriseId))
                .thenReturn(List.of(c4, c42, c421, c422, c43, deleted));
        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(documentRepository.findByInvoiceIdInOrderByUploadedAtAsc(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Rubrica → a sub-árvore viva dela, com o flag ligado")
    void budgetItemExpandsToItsSubtree() {
        service.search(enterpriseId, filterWithBudgetItem(c42.getId()), pageable);

        assertThat(captureBudgetFilter()).isTrue();
        assertThat(captureBudgetItemIds())
                .containsExactlyInAnyOrder(c42.getId(), c421.getId(), c422.getId());
    }

    @Test
    @DisplayName("Sem rubrica → flag desligado e lista de enchimento")
    void noBudgetItemMeansNoFilter() {
        service.search(enterpriseId, InvoiceSearchFilter.basic(null, null, null, null, null, null, null, null), pageable);

        assertThat(captureBudgetFilter()).isFalse();
        assertThat(captureBudgetItemIds()).hasSize(1);
    }

    @Test
    @DisplayName("Rubrica de outra obra (ou apagada) → filtra tudo, não devolve a lista inteira")
    void unknownBudgetItemFiltersEverything() {
        service.search(enterpriseId, filterWithBudgetItem(UUID.randomUUID()), pageable);

        assertThat(captureBudgetFilter()).isTrue();
        assertThat(captureBudgetItemIds()).containsExactly(new UUID(0L, 0L));
    }

    @Test
    @DisplayName("Os restantes filtros chegam normalizados à query")
    void otherFiltersArePassedNormalized() {
        InvoiceSearchFilter filter = new InvoiceSearchFilter(null, null, null, null, null, null, null, " ft ",
                " 123456789 ", "credit_note", " missing ", "partial", "complete",
                new BigDecimal("10"), new BigDecimal("500"), null);

        service.search(enterpriseId, filter, pageable);

        verify(repository).search(eq(enterpriseId), any(), any(), any(), any(), any(), eq(BudgetRowKind.ITEM),
                any(), any(), eq("ft"), eq("123456789"), eq("CREDIT_NOTE"), eq("MISSING"),
                eq(new BigDecimal("10")), eq(new BigDecimal("500")), eq("PARTIAL"), eq("COMPLETE"),
                eq(false), any(), eq(pageable));
    }

    // ── auxiliares ────────────────────────────────────────────

    private static InvoiceSearchFilter filterWithBudgetItem(UUID id) {
        return new InvoiceSearchFilter(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, id);
    }

    private boolean captureBudgetFilter() {
        ArgumentCaptor<Boolean> flag = ArgumentCaptor.forClass(Boolean.class);
        verify(repository).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), flag.capture(), any(), any());
        return flag.getValue();
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> captureBudgetItemIds() {
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(repository).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), ids.capture(), any());
        return ids.getValue();
    }

    private static ConstructionBudgetItem item(Enterprise enterprise, ConstructionBudgetItem parent, String code) {
        ConstructionBudgetItem i = new ConstructionBudgetItem();
        i.setId(UUID.randomUUID());
        i.setEnterprise(enterprise);
        i.setParent(parent);
        i.setRowKind(BudgetRowKind.ITEM);
        i.setCode(code);
        i.setName("Rubrica " + code);
        i.setSortOrder(0);
        return i;
    }
}
