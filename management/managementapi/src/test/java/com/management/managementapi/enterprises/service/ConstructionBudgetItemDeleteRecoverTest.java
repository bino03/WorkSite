package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemDeletedDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Eliminar uma rubrica passou a soft delete (2026-09-16): marca {@code deletedAt}
 * na rubrica e em toda a sub-árvore, nunca apaga a linha na hora — a purga real
 * só corre 30 dias depois, por job agendado. Bloqueado se houver despesas em
 * qualquer nó da sub-árvore ({@code BUDGET_013}). Recuperar repõe a rubrica e a
 * parte da sub-árvore que foi eliminada junto, voltando à mãe original quando
 * ela ainda existe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConstructionBudgetItemDeleteRecoverTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionExpenseRepository expenseRepository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    private ConstructionBudgetItemService service() {
        return new ConstructionBudgetItemService(repository, expenseRepository, enterpriseRepository, authContext);
    }

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private static Enterprise enterprise() {
        Enterprise e = new Enterprise();
        e.setId(ENTERPRISE_ID);
        e.setName("Vila Petrus");
        return e;
    }

    private static ConstructionBudgetItem item(String code, String name, ConstructionBudgetItem parent) {
        ConstructionBudgetItem i = new ConstructionBudgetItem();
        i.setId(UUID.randomUUID());
        i.setEnterprise(enterprise());
        i.setParent(parent);
        i.setRowKind(BudgetRowKind.ITEM);
        i.setCode(code);
        i.setName(name);
        i.setSortOrder(0);
        return i;
    }

    @Test
    @DisplayName("eliminar bloqueia quando há despesas em qualquer nó da sub-árvore")
    void deleteBlockedWhenSubtreeHasExpenses() {
        ConstructionBudgetItem parent = item("4", "Estrutura", null);
        ConstructionBudgetItem child = item("4.1", "Fundações", parent);

        when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of(parent, child));
        when(expenseRepository.existsByBudgetItemIdIn(any())).thenReturn(true);

        assertThatThrownBy(() -> service().delete(parent.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_ITEM_HAS_EXPENSES);

        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("eliminar sem despesas marca deletedAt na rubrica e em toda a sub-árvore")
    void deleteSoftDeletesWholeSubtree() {
        ConstructionBudgetItem parent = item("4", "Estrutura", null);
        ConstructionBudgetItem child = item("4.1", "Fundações", parent);
        ConstructionBudgetItem grandchild = item("4.1.1", "Betão", child);

        when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of(parent, child, grandchild));
        when(expenseRepository.existsByBudgetItemIdIn(any())).thenReturn(false);

        service().delete(parent.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConstructionBudgetItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<ConstructionBudgetItem> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(ConstructionBudgetItem::isDeleted);
    }

    @Test
    @DisplayName("recuperar sem estar eliminada → BUDGET_014")
    void recoverRejectsNonDeletedItem() {
        ConstructionBudgetItem alive = item("4", "Estrutura", null);
        when(repository.findById(alive.getId())).thenReturn(Optional.of(alive));

        assertThatThrownBy(() -> service().recover(alive.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_ITEM_NOT_DELETED);
    }

    @Test
    @DisplayName("recuperar com a mãe ainda viva repõe a rubrica e a sub-árvore eliminada, sem reparentar")
    void recoverRestoresSubtreeUnderOriginalParent() {
        ConstructionBudgetItem parent = item("4", "Estrutura", null); // viva
        ConstructionBudgetItem child = item("4.1", "Fundações", parent);
        ConstructionBudgetItem grandchild = item("4.1.1", "Betão", child);
        OffsetDateTime deletedAt = OffsetDateTime.now().minusDays(3);
        child.setDeletedAt(deletedAt);
        grandchild.setDeletedAt(deletedAt);

        when(repository.findById(child.getId())).thenReturn(Optional.of(child));
        when(repository.findByEnterpriseIdAndCode(ENTERPRISE_ID, "4.1")).thenReturn(Optional.empty());
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of(parent, child, grandchild));

        BudgetItemNodeDTO result = service().recover(child.getId());

        assertThat(child.isDeleted()).isFalse();
        assertThat(grandchild.isDeleted()).isFalse();
        assertThat(child.getParent()).isEqualTo(parent); // não foi reparentada
        assertThat(result.id()).isEqualTo(child.getId());
    }

    @Test
    @DisplayName("recuperar com a mãe entretanto eliminada volta ao topo")
    void recoverGoesToRootWhenParentIsGone() {
        ConstructionBudgetItem parent = item("4", "Estrutura", null);
        parent.setDeletedAt(OffsetDateTime.now().minusDays(10)); // a mãe também foi eliminada
        ConstructionBudgetItem child = item("4.1", "Fundações", parent);
        child.setDeletedAt(OffsetDateTime.now().minusDays(3));

        when(repository.findById(child.getId())).thenReturn(Optional.of(child));
        when(repository.findByEnterpriseIdAndCode(ENTERPRISE_ID, "4.1")).thenReturn(Optional.empty());
        when(repository.nextSortOrder(ENTERPRISE_ID, null)).thenReturn(5);
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID)).thenReturn(List.of(parent, child));

        service().recover(child.getId());

        assertThat(child.getParent()).isNull();
        assertThat(child.getSortOrder()).isEqualTo(5);
        assertThat(child.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("recuperar com o código entretanto reutilizado → BUDGET_DUPLICATE_CODE")
    void recoverRejectsWhenCodeWasReused() {
        ConstructionBudgetItem deleted = item("4.1", "Fundações", null);
        deleted.setDeletedAt(OffsetDateTime.now().minusDays(1));
        ConstructionBudgetItem takenBySomeoneElse = item("4.1", "Outra rubrica", null);

        when(repository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        when(repository.findByEnterpriseIdAndCode(ENTERPRISE_ID, "4.1"))
                .thenReturn(Optional.of(takenBySomeoneElse));

        assertThatThrownBy(() -> service().recover(deleted.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_DUPLICATE_CODE);
    }

    @Test
    @DisplayName("mover para cima troca de posição com o irmão anterior (setas ↑ ↓ da árvore)")
    void moveSwapsWithPreviousSibling() {
        ConstructionBudgetItem parent = item("4", "Estrutura", null);
        ConstructionBudgetItem a = item("4.1", "A", parent);
        a.setSortOrder(0);
        ConstructionBudgetItem b = item("4.2", "B", parent);
        b.setSortOrder(1);
        ConstructionBudgetItem c = item("4.3", "C", parent);
        c.setSortOrder(2);

        when(repository.findById(b.getId())).thenReturn(Optional.of(b));
        when(repository.findById(parent.getId())).thenReturn(Optional.of(parent));
        // "subir" B: pede o sortOrder do irmão anterior (A = 0)
        when(repository.findByParentIdOrderBySortOrderAsc(parent.getId())).thenReturn(List.of(a, b, c));
        when(repository.findTreeByEnterpriseId(ENTERPRISE_ID))
                .thenReturn(List.of(parent, a, b, c));

        service().move(b.getId(), parent.getId(), 0);

        assertThat(b.getSortOrder()).isZero();
        assertThat(a.getSortOrder()).isEqualTo(1);
        assertThat(c.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("listDeleted devolve as eliminadas com a data de purga a 30 dias")
    void listDeletedComputesPurgeDate() {
        ConstructionBudgetItem deleted = item("4.1", "Fundações", null);
        OffsetDateTime deletedAt = OffsetDateTime.now().minusDays(5);
        deleted.setDeletedAt(deletedAt);

        when(repository.findByEnterpriseIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(ENTERPRISE_ID))
                .thenReturn(List.of(deleted));

        List<BudgetItemDeletedDTO> result = service().listDeleted(ENTERPRISE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deletedAt()).isEqualTo(deletedAt);
        assertThat(result.get(0).purgeAt()).isEqualTo(deletedAt.plusDays(30));
    }
}
