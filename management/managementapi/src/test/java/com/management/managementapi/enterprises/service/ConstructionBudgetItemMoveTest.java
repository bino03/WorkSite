package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * "Subir" e "Descer" na lista do orçamento. O {@code sortOrder} pedido é a
 * posição final entre os irmãos vivos — o desempate antigo ("o movido fica à
 * frente de quem tiver o mesmo sortOrder") fazia o "Descer" não sair do sítio
 * (apontado pelo utilizador a 2026-09-17).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConstructionBudgetItemMoveTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionBudgetRepository budgetRepository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private ConstructionBudgetItem chapter, a, b, c, deleted;
    private List<ConstructionBudgetItem> all;

    @BeforeEach
    void setUp() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);

        chapter = item(enterprise, null, "1", 0);
        a = item(enterprise, chapter, "1.1", 0);
        b = item(enterprise, chapter, "1.2", 1);
        c = item(enterprise, chapter, "1.3", 2);
        deleted = item(enterprise, chapter, "1.9", 3);
        deleted.setDeletedAt(java.time.OffsetDateTime.now());
        all = List.of(chapter, a, b, c, deleted);

        for (ConstructionBudgetItem i : all) {
            when(repository.findById(i.getId())).thenReturn(Optional.of(i));
        }
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenReturn(all);
        when(repository.findByParentIdOrderBySortOrderAsc(chapter.getId())).thenAnswer(inv ->
                all.stream()
                        .filter(i -> i.getParent() == chapter && !i.isDeleted())
                        .sorted(Comparator.comparingInt(ConstructionBudgetItem::getSortOrder))
                        .toList());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.findAllByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of());
    }

    private static ConstructionBudgetItem item(Enterprise enterprise, ConstructionBudgetItem parent,
                                               String code, int sortOrder) {
        ConstructionBudgetItem i = new ConstructionBudgetItem();
        i.setId(UUID.randomUUID());
        i.setEnterprise(enterprise);
        i.setParent(parent);
        i.setRowKind(BudgetRowKind.ITEM);
        i.setCode(code);
        i.setName("Rubrica " + code);
        i.setSortOrder(sortOrder);
        return i;
    }

    private ConstructionBudgetItemService service() {
        return new ConstructionBudgetItemService(repository, budgetRepository, expenseRepository, enterpriseRepository, authContext);
    }

    private List<String> order() {
        return all.stream()
                .filter(i -> i.getParent() == chapter && !i.isDeleted())
                .sorted(Comparator.comparingInt(ConstructionBudgetItem::getSortOrder))
                .map(ConstructionBudgetItem::getCode)
                .toList();
    }

    @Test
    @DisplayName("Descer um lugar troca com o irmão de baixo")
    void moveDownSwapsWithNextSibling() {
        service().move(a.getId(), chapter.getId(), 1);
        assertThat(order()).containsExactly("1.2", "1.1", "1.3");
    }

    @Test
    @DisplayName("Subir um lugar troca com o irmão de cima")
    void moveUpSwapsWithPreviousSibling() {
        service().move(c.getId(), chapter.getId(), 1);
        assertThat(order()).containsExactly("1.1", "1.3", "1.2");
    }

    @Test
    @DisplayName("Descer para o fim e subir para o topo")
    void moveToEitherEnd() {
        service().move(a.getId(), chapter.getId(), 2);
        assertThat(order()).containsExactly("1.2", "1.3", "1.1");

        service().move(a.getId(), chapter.getId(), 0);
        assertThat(order()).containsExactly("1.1", "1.2", "1.3");
    }

    @Test
    @DisplayName("Os irmãos ficam com posições consecutivas e uma rubrica eliminada não conta")
    void resequencesLiveSiblingsOnly() {
        service().move(b.getId(), chapter.getId(), 2);
        assertThat(order()).containsExactly("1.1", "1.3", "1.2");
        assertThat(a.getSortOrder()).isEqualTo(0);
        assertThat(c.getSortOrder()).isEqualTo(1);
        assertThat(b.getSortOrder()).isEqualTo(2);
        assertThat(deleted.getSortOrder()).isEqualTo(3);
    }
}
