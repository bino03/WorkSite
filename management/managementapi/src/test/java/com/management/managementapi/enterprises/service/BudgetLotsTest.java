package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.request.BudgetItemUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.request.BudgetLotUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSearchResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetLotDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vários orçamentos por projeto — um por lote/edifício (V39). O que muda: o
 * código é único dentro do lote (o 4.2.1 repete-se entre lotes), a rubrica-mãe
 * tem de ser do mesmo lote, e a pesquisa da vila diz de que lote é cada rubrica.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetLotsTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionBudgetRepository budgetRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private Enterprise enterprise;
    private ConstructionBudget lotA, lotB;
    private final List<ConstructionBudgetItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setName("Vila de dois lotes");
        lotA = lot("Lote A", 0);
        lotB = lot("Lote B", 1);

        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(enterprise));
        when(budgetRepository.findById(lotA.getId())).thenReturn(Optional.of(lotA));
        when(budgetRepository.findById(lotB.getId())).thenReturn(Optional.of(lotB));
        when(budgetRepository.findByEnterpriseIdOrderBySortOrderAscCreatedAtAsc(ENTERPRISE_ID))
                .thenReturn(List.of(lotA, lotB));
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenAnswer(inv -> List.copyOf(items));
        when(expenseRepository.findAllByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> {
            ConstructionBudgetItem saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            items.add(saved);
            return saved;
        });
        when(repository.findById(any())).thenAnswer(inv -> items.stream()
                .filter(i -> i.getId().equals(inv.getArgument(0))).findFirst());
    }

    @Test
    @DisplayName("O mesmo código noutro lote é aceite — a unicidade é por lote")
    void sameCodeInAnotherLotIsAllowed() {
        item(lotA, null, "4.2.1", "Betão", "100");
        when(repository.findByBudgetIdAndCode(lotB.getId(), "4.2.1")).thenReturn(Optional.empty());

        service().create(upsert(lotB.getId(), null, "4.2.1"));

        ConstructionBudgetItem created = items.get(items.size() - 1);
        assertThat(created.getBudget()).isSameAs(lotB);
        assertThat(created.getEnterprise()).isSameAs(enterprise);
    }

    @Test
    @DisplayName("O mesmo código no mesmo lote continua a ser recusado")
    void sameCodeInSameLotIsRejected() {
        ConstructionBudgetItem existing = item(lotA, null, "4.2.1", "Betão", "100");
        when(repository.findByBudgetIdAndCode(lotA.getId(), "4.2.1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().create(upsert(lotA.getId(), null, "4.2.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_DUPLICATE_CODE);
    }

    @Test
    @DisplayName("Uma rubrica-mãe de outro lote é recusada")
    void parentFromAnotherLotIsRejected() {
        ConstructionBudgetItem chapterA = item(lotA, null, "4", "Estrutura", null);

        assertThatThrownBy(() -> service().create(upsert(lotB.getId(), chapterA.getId(), "4.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_PARENT_OTHER_LOT);
    }

    @Test
    @DisplayName("A pesquisa da vila devolve o 4.2.1 dos dois lotes, com o lote no caminho")
    void searchAcrossLotsPrefixesLotName() {
        item(lotA, null, "4", "Estrutura", "100");
        item(lotB, null, "4", "Estrutura", "250");

        List<BudgetItemSearchResultDTO> found = service().search(ENTERPRISE_ID, "4", 20);

        assertThat(found).extracting(BudgetItemSearchResultDTO::budgetName).containsExactly("Lote A", "Lote B");
        assertThat(found).extracting(BudgetItemSearchResultDTO::path)
                .containsExactly("Lote A › 4 Estrutura", "Lote B › 4 Estrutura");
    }

    @Test
    @DisplayName("Com um só lote o caminho fica como era — sem prefixo")
    void singleLotKeepsPathUnprefixed() {
        item(lotA, null, "4", "Estrutura", "100");

        assertThat(service().search(ENTERPRISE_ID, "4", 20))
                .extracting(BudgetItemSearchResultDTO::path).containsExactly("4 Estrutura");
    }

    @Test
    @DisplayName("Cada lote tem o seu total; a vila é a soma")
    void lotTotals() {
        item(lotA, null, "1", "Estaleiro", "100");
        item(lotB, null, "1", "Estaleiro", "250");

        List<BudgetLotDTO> lots = service().listLots(ENTERPRISE_ID);

        assertThat(lots).extracting(BudgetLotDTO::budgetTotal)
                .containsExactly(new BigDecimal("100"), new BigDecimal("250"));
        assertThat(service().getTree(ENTERPRISE_ID).budgetTotal()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("Apagar um lote com despesas é recusado")
    void deleteLotWithExpensesIsRejected() {
        when(repository.budgetHasExpenses(lotA.getId())).thenReturn(true);

        assertThatThrownBy(() -> service().deleteLot(lotA.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_LOT_HAS_EXPENSES);
        verify(budgetRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Nome de lote repetido no projeto é recusado")
    void duplicateLotNameIsRejected() {
        when(budgetRepository.existsByEnterpriseIdAndNameIgnoreCase(ENTERPRISE_ID, "Lote A")).thenReturn(true);

        assertThatThrownBy(() -> service().createLot(ENTERPRISE_ID,
                new BudgetLotUpsertDTO(" Lote A ", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_LOT_DUPLICATE_NAME);
    }

    @Test
    @DisplayName("A coluna Rubrica lê o lote antes do índice, e só quando é mesmo um lote")
    void rubricCellSplitsLot() {
        assertThat(DespesasExcelImportService.splitLot("Lote A · 8.2 — Betão"))
                .containsExactly("Lote A", "8.2 — Betão");
        assertThat(DespesasExcelImportService.splitLot("8.2 — Betão · armado")[0]).isNull();
        assertThat(DespesasExcelImportService.splitLot("8.2 · Betão")[0]).isNull();
        assertThat(DespesasExcelImportService.normalizeRubricCode(
                DespesasExcelImportService.splitLot("Moradia 2 · 4.2. — Lajes")[1])).isEqualTo("4.2");
    }

    // ── auxiliares ────────────────────────────────────────────

    private ConstructionBudget lot(String name, int sortOrder) {
        ConstructionBudget lot = new ConstructionBudget();
        lot.setId(UUID.randomUUID());
        lot.setEnterprise(enterprise);
        lot.setName(name);
        lot.setSortOrder(sortOrder);
        return lot;
    }

    private ConstructionBudgetItem item(ConstructionBudget lot, ConstructionBudgetItem parent,
                                        String code, String name, String total) {
        ConstructionBudgetItem i = new ConstructionBudgetItem();
        i.setId(UUID.randomUUID());
        i.setEnterprise(enterprise);
        i.setBudget(lot);
        i.setParent(parent);
        i.setRowKind(BudgetRowKind.ITEM);
        i.setCode(code);
        i.setName(name);
        i.setSortOrder(0);
        i.setTotalPrice(total == null ? null : new BigDecimal(total));
        items.add(i);
        return i;
    }

    private static BudgetItemUpsertDTO upsert(UUID budgetId, UUID parentId, String code) {
        return new BudgetItemUpsertDTO(budgetId, parentId, BudgetRowKind.ITEM, code, "Rubrica",
                null, null, null, null, null, null, null, null, null);
    }

    private ConstructionBudgetItemService service() {
        return new ConstructionBudgetItemService(repository, budgetRepository, expenseRepository,
                enterpriseRepository, authContext);
    }
}
