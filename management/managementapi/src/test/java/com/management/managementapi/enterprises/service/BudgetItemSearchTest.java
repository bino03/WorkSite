package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSearchResultDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A pesquisa de rubricas do ecrã de classificação e do "mover para outra
 * rubrica". Por código é prefixo <b>no mesmo nível</b>: "2" não pode trazer a
 * 2.1 nem a 20.1 (apontado pelo utilizador a 2026-09-21 com um screenshot).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetItemSearchTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private Enterprise enterprise;
    private final List<ConstructionBudgetItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        enterprise = new Enterprise();
        enterprise.setId(ENTERPRISE_ID);
        enterprise.setName("Obra de teste");

        ConstructionBudgetItem c2 = item(null, "2", "DEMOLIÇÕES", 0);
        ConstructionBudgetItem c21 = item(c2, "2.1", "Execução de demolição geral", 0);
        item(c21, "2.1.1", "Transporte a vazadouro", 0);
        item(c2, "2.10", "Betão de limpeza", 1);
        ConstructionBudgetItem c12 = item(null, "12", "SERRALHARIAS", 1);
        item(c12, "12.1", "Guardas em betão", 0);
        ConstructionBudgetItem c20 = item(null, "20", "INFRAESTRUTURAS", 2);
        item(c20, "20.1", "Lancis de cimento", 0);

        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(enterprise));
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenAnswer(inv -> List.copyOf(items));
        when(expenseRepository.findAllByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("\"2\" dá a 2 e a 20 — nem as filhas delas, nem a 12")
    void codeQueryMatchesPrefixAtSameLevel() {
        assertThat(codes("2")).containsExactly("2", "20");
    }

    @Test
    @DisplayName("\"2.\" abre o capítulo: as filhas diretas da 2, sem as netas")
    void trailingDotListsDirectChildren() {
        assertThat(codes("2.")).containsExactly("2.1", "2.10");
    }

    @Test
    @DisplayName("\"2.1\" dá a 2.1 e a 2.10, não a 2.1.1")
    void deeperCodeStaysAtItsLevel() {
        assertThat(codes("2.1")).containsExactly("2.1", "2.10");
        assertThat(codes("2.1.")).containsExactly("2.1.1");
    }

    @Test
    @DisplayName("Texto continua a procurar em toda a árvore, pelo nome")
    void textQuerySearchesWholeTree() {
        assertThat(codes("betão")).containsExactlyInAnyOrder("2.10", "12.1");
    }

    // ── auxiliares ────────────────────────────────────────────

    private List<String> codes(String query) {
        return service().search(ENTERPRISE_ID, query, 50).stream()
                .map(BudgetItemSearchResultDTO::code)
                .toList();
    }

    private ConstructionBudgetItem item(ConstructionBudgetItem parent, String code, String name, int sortOrder) {
        ConstructionBudgetItem i = new ConstructionBudgetItem();
        i.setId(UUID.randomUUID());
        i.setEnterprise(enterprise);
        i.setParent(parent);
        i.setRowKind(BudgetRowKind.ITEM);
        i.setCode(code);
        i.setName(name);
        i.setSortOrder(sortOrder);
        items.add(i);
        return i;
    }

    private ConstructionBudgetItemService service() {
        return new ConstructionBudgetItemService(repository, expenseRepository, enterpriseRepository, authContext);
    }
}
