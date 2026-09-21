package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
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
import static org.mockito.Mockito.when;

/**
 * Uma despesa lançada numa rubrica com sub-rubricas aparece repartida em partes
 * iguais por elas (pedido do utilizador a 2026-09-21: "15 € na 4.3 → 5 € em
 * cada uma de 4.3.1, 4.3.2 e 4.3.3"). A despesa fica gravada na 4.3; só a
 * leitura reparte, e a soma nunca pode passar do que foi lançado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetSpentDistributionTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private Enterprise enterprise;
    private ConstructionBudgetItem chapter, parent, c1, c2, c3;
    private final List<ConstructionBudgetItem> items = new ArrayList<>();
    private final List<ConstructionExpense> expenses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setName("Obra de teste");

        chapter = item(null, "4", 0, BudgetRowKind.ITEM, null);
        parent = item(chapter, "4.3", 0, BudgetRowKind.ITEM, null);
        c1 = item(parent, "4.3.1", 0, BudgetRowKind.ITEM, new BigDecimal("100"));
        c2 = item(parent, "4.3.2", 1, BudgetRowKind.ITEM, new BigDecimal("100"));
        c3 = item(parent, "4.3.3", 2, BudgetRowKind.ITEM, new BigDecimal("100"));

        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(enterprise));
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenAnswer(inv -> List.copyOf(items));
        when(expenseRepository.findAllByEnterpriseId(ENTERPRISE_ID)).thenAnswer(inv -> List.copyOf(expenses));
    }

    @Test
    @DisplayName("15 € na 4.3 aparecem como 5 € em cada uma das três sub-rubricas")
    void splitsEvenlyAcrossChildren() {
        expense(parent, "15");

        BudgetTreeDTO tree = service().getTree(ENTERPRISE_ID);
        BudgetItemNodeDTO n43 = find(tree.roots(), "4.3");

        assertThat(spent(n43, "4.3.1")).isEqualByComparingTo("5");
        assertThat(spent(n43, "4.3.2")).isEqualByComparingTo("5");
        assertThat(spent(n43, "4.3.3")).isEqualByComparingTo("5");
        assertThat(n43.spentTotal()).isEqualByComparingTo("15");
        assertThat(find(tree.roots(), "4").spentTotal()).isEqualByComparingTo("15");
        assertThat(tree.spentTotal()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("Os cêntimos do arredondamento vão para a última filha — a soma é exata")
    void roundingNeverCreatesOrLosesCents() {
        expense(parent, "10");

        BudgetItemNodeDTO n43 = find(service().getTree(ENTERPRISE_ID).roots(), "4.3");

        assertThat(spent(n43, "4.3.1")).isEqualByComparingTo("3.33");
        assertThat(spent(n43, "4.3.2")).isEqualByComparingTo("3.33");
        assertThat(spent(n43, "4.3.3")).isEqualByComparingTo("3.34");
        assertThat(n43.spentTotal()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("A parte herdada soma-se ao que a sub-rubrica já tinha, e desce até às folhas")
    void inheritedShareAddsToOwnAndFlowsDown() {
        item(c1, "4.3.1.1", 0, BudgetRowKind.ITEM, new BigDecimal("50"));
        item(c1, "4.3.1.2", 1, BudgetRowKind.ITEM, new BigDecimal("50"));
        expense(parent, "15");
        expense(c2, "7");

        BudgetTreeDTO tree = service().getTree(ENTERPRISE_ID);
        BudgetItemNodeDTO n43 = find(tree.roots(), "4.3");
        BudgetItemNodeDTO n431 = find(n43.children(), "4.3.1");

        assertThat(n431.spentTotal()).isEqualByComparingTo("5");
        assertThat(spent(n431, "4.3.1.1")).isEqualByComparingTo("2.50");
        assertThat(spent(n431, "4.3.1.2")).isEqualByComparingTo("2.50");
        assertThat(spent(n43, "4.3.2")).isEqualByComparingTo("12");
        assertThat(spent(n43, "4.3.3")).isEqualByComparingTo("5");
        assertThat(n43.spentTotal()).isEqualByComparingTo("22");
        assertThat(tree.spentTotal()).isEqualByComparingTo("22");
        // As contagens continuam a ser as reais: a despesa é da 4.3, não das filhas.
        assertThat(n43.ownExpenseCount()).isEqualTo(1);
        assertThat(find(n43.children(), "4.3.1").ownExpenseCount()).isZero();
        assertThat(tree.expenseCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Notas não recebem parte; sem sub-rubricas o gasto fica na própria rubrica")
    void notesAreSkippedAndLeavesKeepTheirOwn() {
        item(parent, null, 3, BudgetRowKind.NOTE, null);
        expense(parent, "15");
        expense(c3, "4");

        BudgetTreeDTO tree = service().getTree(ENTERPRISE_ID);
        BudgetItemNodeDTO n43 = find(tree.roots(), "4.3");

        assertThat(n43.children()).hasSize(4);
        assertThat(n43.children().get(3).spentTotal()).isEqualByComparingTo("0");
        assertThat(spent(n43, "4.3.1")).isEqualByComparingTo("5");
        assertThat(spent(n43, "4.3.3")).isEqualByComparingTo("9");
        assertThat(n43.spentTotal()).isEqualByComparingTo("19");
    }

    @Test
    @DisplayName("O GET de um nó isolado traz a parte que herda dos pais")
    void singleNodeIncludesInheritedShare() {
        expense(parent, "15");
        when(repository.findById(c2.getId())).thenReturn(Optional.of(c2));

        assertThat(service().getNode(c2.getId()).spentTotal()).isEqualByComparingTo("5");
    }

    // ── auxiliares ────────────────────────────────────────────

    private ConstructionBudgetItem item(ConstructionBudgetItem parent, String code, int sortOrder,
                                        BudgetRowKind kind, BigDecimal totalPrice) {
        ConstructionBudgetItem i = new ConstructionBudgetItem();
        i.setId(UUID.randomUUID());
        i.setEnterprise(enterprise);
        i.setParent(parent);
        i.setRowKind(kind);
        i.setCode(code);
        i.setName(code == null ? "(nota)" : "Rubrica " + code);
        i.setSortOrder(sortOrder);
        i.setTotalPrice(totalPrice);
        items.add(i);
        return i;
    }

    private void expense(ConstructionBudgetItem item, String amount) {
        ConstructionExpense e = new ConstructionExpense();
        e.setId(UUID.randomUUID());
        e.setBudgetItem(item);
        e.setTotalPrice(new BigDecimal(amount));
        expenses.add(e);
    }

    private ConstructionBudgetItemService service() {
        return new ConstructionBudgetItemService(repository, expenseRepository, enterpriseRepository, authContext);
    }

    private static BudgetItemNodeDTO find(List<BudgetItemNodeDTO> nodes, String code) {
        for (BudgetItemNodeDTO node : nodes) {
            if (code.equals(node.code())) return node;
            BudgetItemNodeDTO found = find(node.children(), code);
            if (found != null) return found;
        }
        return null;
    }

    private static BigDecimal spent(BudgetItemNodeDTO parent, String code) {
        return find(parent.children(), code).spentTotal();
    }

}
