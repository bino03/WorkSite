package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.request.BudgetItemUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSaveResponseDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.dto.budget.response.DatePropagationHintDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestão da árvore de rubricas do orçamento da obra.
 *
 * A árvore é sempre lida de uma vez (uma query para as rubricas, outra para as
 * despesas) e agregada em memória — um orçamento ronda as 200 linhas, por isso
 * sai muito mais barato do que descer nível a nível.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionBudgetItemService {

    /** Tolerância ao comparar o total escrito no Excel com a soma das folhas. */
    private static final BigDecimal MISMATCH_TOLERANCE = new BigDecimal("0.01");

    private final ConstructionBudgetItemRepository repository;
    private final ConstructionExpenseRepository expenseRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final AuthContext authContext;

    // ── leitura ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BudgetTreeDTO getTree(UUID enterpriseId) {
        if (!enterpriseRepository.existsById(enterpriseId)) {
            throw new BusinessException(ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND);
        }

        List<ConstructionBudgetItem> items = repository.findTreeByEnterpriseId(enterpriseId);
        Map<UUID, ExpenseRollup> expensesByItem = loadExpenseRollups(enterpriseId);
        Map<UUID, List<ConstructionBudgetItem>> childrenByParent = groupByParent(items);

        List<BudgetItemNodeDTO> roots = new ArrayList<>();
        for (ConstructionBudgetItem root : childrenByParent.getOrDefault(null, List.of())) {
            roots.add(buildNode(root, childrenByParent, expensesByItem, 0));
        }

        BigDecimal budgetTotal = roots.stream()
                .map(BudgetItemNodeDTO::rolledUpBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spentTotal = roots.stream()
                .map(BudgetItemNodeDTO::spentTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int expenseCount = roots.stream().mapToInt(BudgetItemNodeDTO::expenseCount).sum();
        int missingInvoice = roots.stream().mapToInt(BudgetItemNodeDTO::missingInvoiceCount).sum();
        int pendingAccountant = roots.stream().mapToInt(BudgetItemNodeDTO::pendingAccountantCount).sum();
        BigDecimal pendingAccountantTotal = roots.stream()
                .map(BudgetItemNodeDTO::pendingAccountantTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OverBudget overBudget = new OverBudget();
        roots.forEach(root -> collectOverBudget(root, overBudget));

        return new BudgetTreeDTO(
                enterpriseId,
                budgetTotal,
                spentTotal,
                budgetTotal.subtract(spentTotal),
                percentage(spentTotal, budgetTotal),
                items.size(),
                expenseCount,
                overBudget.count,
                overBudget.amount,
                missingInvoice,
                pendingAccountant,
                pendingAccountantTotal,
                roots);
    }

    @Transactional(readOnly = true)
    public ConstructionBudgetItem getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.budgetItem(id.toString()));
    }

    /** Um nó com a sua sub-árvore e agregados — usado no GET individual. */
    @Transactional(readOnly = true)
    public BudgetItemNodeDTO getNode(UUID id) {
        ConstructionBudgetItem item = getById(id);
        UUID enterpriseId = item.getEnterprise().getId();

        Map<UUID, List<ConstructionBudgetItem>> childrenByParent =
                groupByParent(repository.findTreeByEnterpriseId(enterpriseId));
        Map<UUID, ExpenseRollup> expensesByItem = loadExpenseRollups(enterpriseId);

        return buildNode(item, childrenByParent, expensesByItem, depthOf(item));
    }

    // ── escrita ───────────────────────────────────────────────

    public BudgetItemSaveResponseDTO create(BudgetItemUpsertDTO dto) {
        Enterprise enterprise = enterpriseRepository.findById(dto.enterpriseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND));

        ConstructionBudgetItem parent = resolveParent(dto.parentId(), enterprise.getId());
        validateDates(dto);
        validateCodeIsFree(enterprise.getId(), dto.code(), null);

        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setEnterprise(enterprise);
        item.setParent(parent);
        item.setSortOrder(repository.nextSortOrder(enterprise.getId(),
                parent == null ? null : parent.getId()));
        authContext.currentProfileId().ifPresent(item::setCreatedBy);
        applyFields(item, dto);

        item = repository.save(item);
        List<DatePropagationHintDTO> hints = handleDatePropagation(item, dto);

        return new BudgetItemSaveResponseDTO(getNode(item.getId()), hints);
    }

    public BudgetItemSaveResponseDTO update(UUID id, BudgetItemUpsertDTO dto) {
        ConstructionBudgetItem item = getById(id);
        UUID enterpriseId = item.getEnterprise().getId();

        // O projeto de uma rubrica não muda. Um PUT que peça outro é rejeitado
        // em vez de silenciosamente ignorado, para o cliente saber que não pegou.
        if (dto.enterpriseId() != null && !dto.enterpriseId().equals(enterpriseId)) {
            throw new BusinessException(ErrorCode.BUDGET_ITEM_OTHER_ENTERPRISE);
        }

        validateDates(dto);
        validateCodeIsFree(enterpriseId, dto.code(), id);

        UUID currentParentId = item.getParent() == null ? null : item.getParent().getId();
        if (!Objects.equals(currentParentId, dto.parentId())) {
            ConstructionBudgetItem newParent = resolveParent(dto.parentId(), enterpriseId);
            assertNoCycle(item, newParent);
            item.setParent(newParent);
            item.setSortOrder(repository.nextSortOrder(enterpriseId, dto.parentId()));
        }

        applyFields(item, dto);
        item = repository.save(item);
        List<DatePropagationHintDTO> hints = handleDatePropagation(item, dto);

        return new BudgetItemSaveResponseDTO(getNode(item.getId()), hints);
    }

    /** Reordena entre irmãos e/ou muda de rubrica-mãe. */
    public BudgetItemNodeDTO move(UUID id, UUID newParentId, Integer newSortOrder) {
        ConstructionBudgetItem item = getById(id);
        UUID enterpriseId = item.getEnterprise().getId();

        ConstructionBudgetItem newParent = resolveParent(newParentId, enterpriseId);
        assertNoCycle(item, newParent);
        item.setParent(newParent);
        item.setSortOrder(newSortOrder != null
                ? newSortOrder
                : repository.nextSortOrder(enterpriseId, newParentId));
        repository.save(item);

        resequenceSiblings(enterpriseId, newParentId, item.getId(), item.getSortOrder());
        return getNode(item.getId());
    }

    /** Elimina a rubrica e toda a sub-árvore (cascata na BD, incluindo despesas). */
    public void delete(UUID id) {
        repository.delete(getById(id));
    }

    // ── construção da árvore ──────────────────────────────────

    private Map<UUID, List<ConstructionBudgetItem>> groupByParent(List<ConstructionBudgetItem> items) {
        Map<UUID, List<ConstructionBudgetItem>> byParent = new LinkedHashMap<>();
        for (ConstructionBudgetItem item : items) {
            UUID parentId = item.getParent() == null ? null : item.getParent().getId();
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(item);
        }
        byParent.values().forEach(list -> list.sort(
                Comparator.comparing(ConstructionBudgetItem::getSortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder()))));
        return byParent;
    }

    private Map<UUID, ExpenseRollup> loadExpenseRollups(UUID enterpriseId) {
        Map<UUID, ExpenseRollup> byItem = new HashMap<>();
        for (ConstructionExpense expense : expenseRepository.findAllByEnterpriseId(enterpriseId)) {
            byItem.computeIfAbsent(expense.getBudgetItem().getId(), k -> new ExpenseRollup())
                    .add(expense.getTotalPrice(), expense.isSentToAccountant(),
                            expense.getStorageKey() != null);
        }
        return byItem;
    }

    /**
     * Agrega a sub-árvore de baixo para cima.
     *
     * O orçamento efectivo de um nó é a soma das folhas com preço. Só quando
     * nenhum descendente tem preço — o caso de uma rubrica cujos filhos são
     * apenas notas de contexto — é que vale o total escrito na própria linha.
     * Somar os dois duplicaria: o Excel guarda o total do capítulo <i>e</i> o
     * detalhe das rubricas.
     */
    private BudgetItemNodeDTO buildNode(ConstructionBudgetItem item,
                                        Map<UUID, List<ConstructionBudgetItem>> childrenByParent,
                                        Map<UUID, ExpenseRollup> expensesByItem,
                                        int depth) {

        List<BudgetItemNodeDTO> children = new ArrayList<>();
        for (ConstructionBudgetItem child : childrenByParent.getOrDefault(item.getId(), List.of())) {
            children.add(buildNode(child, childrenByParent, expensesByItem, depth + 1));
        }

        BigDecimal childSum = BigDecimal.ZERO;
        boolean childHasPrice = false;
        BigDecimal childSpent = BigDecimal.ZERO;
        int childExpenses = 0;
        int childMissingInvoice = 0;
        int childPendingAccountant = 0;
        BigDecimal childPendingAccountantTotal = BigDecimal.ZERO;
        for (BudgetItemNodeDTO child : children) {
            childSum = childSum.add(child.rolledUpBudget());
            childSpent = childSpent.add(child.spentTotal());
            childExpenses += child.expenseCount();
            childMissingInvoice += child.missingInvoiceCount();
            childPendingAccountant += child.pendingAccountantCount();
            childPendingAccountantTotal = childPendingAccountantTotal.add(child.pendingAccountantTotal());
            if (child.rolledUpBudget().signum() != 0 || child.totalPrice() != null) {
                childHasPrice = true;
            }
        }

        BigDecimal own = item.getTotalPrice();
        BigDecimal rolledUp = childHasPrice ? childSum : (own != null ? own : BigDecimal.ZERO);

        // Só há divergência a reportar quando há os dois lados para comparar:
        // um total escrito na linha e descendentes com preço.
        BigDecimal variance = (own != null && childHasPrice) ? own.subtract(childSum) : null;
        boolean mismatch = variance != null
                && variance.abs().compareTo(MISMATCH_TOLERANCE) > 0;

        ExpenseRollup ownExpenses = expensesByItem.getOrDefault(item.getId(), ExpenseRollup.EMPTY);
        BigDecimal spent = childSpent.add(ownExpenses.total());
        int expenseCount = childExpenses + ownExpenses.count();
        int missingInvoice = childMissingInvoice + ownExpenses.missingInvoice();
        int pendingAccountant = childPendingAccountant + ownExpenses.pendingAccountant();
        BigDecimal pendingAccountantTotal =
                childPendingAccountantTotal.add(ownExpenses.pendingAccountantTotal());

        // Só faz sentido falar em derrapagem quando há orçamento contra o qual comparar.
        boolean overBudget = rolledUp.signum() > 0 && spent.compareTo(rolledUp) > 0;

        return new BudgetItemNodeDTO(
                item.getId(),
                item.getParent() == null ? null : item.getParent().getId(),
                item.getRowKind(),
                item.getRowKind().acceptsExpenses(),
                item.getCode(),
                item.getSortOrder() == null ? 0 : item.getSortOrder(),
                depth,
                item.getName(),
                item.getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getObservations(),
                item.getStartDate(),
                item.getEndDate(),
                rolledUp,
                mismatch,
                variance,
                spent,
                rolledUp.subtract(spent),
                percentage(spent, rolledUp),
                overBudget,
                expenseCount,
                ownExpenses.count(),
                missingInvoice,
                pendingAccountant,
                pendingAccountantTotal,
                item.getCreatedAt(),
                item.getUpdatedAt(),
                children);
    }

    /**
     * Recolhe as rubricas em derrapagem, parando na primeira de cada ramo.
     *
     * Se um capítulo passou do orçamento, a causa está nas rubricas lá dentro —
     * contar as duas somaria a mesma derrapagem duas vezes. Contando só o nó
     * mais acima, {@code amount} fica a ser o excesso real do projeto.
     */
    private void collectOverBudget(BudgetItemNodeDTO node, OverBudget acc) {
        if (node.overBudget()) {
            acc.count++;
            acc.amount = acc.amount.add(node.spentTotal().subtract(node.rolledUpBudget()));
            return; // não desce: a derrapagem deste ramo já está contada
        }
        node.children().forEach(child -> collectOverBudget(child, acc));
    }

    /** Acumulador da derrapagem do projeto. */
    private static final class OverBudget {
        int count = 0;
        BigDecimal amount = BigDecimal.ZERO;
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private int depthOf(ConstructionBudgetItem item) {
        int depth = 0;
        ConstructionBudgetItem cursor = item.getParent();
        while (cursor != null) {
            depth++;
            cursor = cursor.getParent();
        }
        return depth;
    }

    // ── validações e auxiliares ───────────────────────────────

    private void applyFields(ConstructionBudgetItem item, BudgetItemUpsertDTO dto) {
        item.setRowKind(dto.rowKind() == null ? BudgetRowKind.ITEM : dto.rowKind());
        item.setCode(blankToNull(dto.code()));
        item.setName(dto.name());
        item.setUnit(blankToNull(dto.unit()));
        item.setQuantity(dto.quantity());
        item.setUnitPrice(dto.unitPrice());
        item.setTotalPrice(dto.totalPrice());
        item.setObservations(blankToNull(dto.observations()));
        item.setStartDate(dto.startDate());
        item.setEndDate(dto.endDate());
    }

    private ConstructionBudgetItem resolveParent(UUID parentId, UUID enterpriseId) {
        if (parentId == null) {
            return null;
        }
        ConstructionBudgetItem parent = repository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_PARENT_NOT_FOUND));
        if (!parent.getEnterprise().getId().equals(enterpriseId)) {
            throw new BusinessException(ErrorCode.BUDGET_PARENT_OTHER_ENTERPRISE);
        }
        return parent;
    }

    /** Impede que uma rubrica seja arrastada para dentro da sua própria sub-árvore. */
    private void assertNoCycle(ConstructionBudgetItem item, ConstructionBudgetItem newParent) {
        ConstructionBudgetItem cursor = newParent;
        while (cursor != null) {
            if (cursor.getId().equals(item.getId())) {
                throw new BusinessException(ErrorCode.BUDGET_CYCLE);
            }
            cursor = cursor.getParent();
        }
    }

    private void validateCodeIsFree(UUID enterpriseId, String code, UUID currentItemId) {
        String normalized = blankToNull(code);
        if (normalized == null) {
            return;
        }
        repository.findByEnterpriseIdAndCode(enterpriseId, normalized).ifPresent(existing -> {
            if (!existing.getId().equals(currentItemId)) {
                throw new BusinessException(ErrorCode.BUDGET_DUPLICATE_CODE);
            }
        });
    }

    private void validateDates(BudgetItemUpsertDTO dto) {
        if (dto.startDate() != null && dto.endDate() != null && dto.endDate().isBefore(dto.startDate())) {
            throw new BusinessException(ErrorCode.BUDGET_INVALID_DATES);
        }
    }

    /**
     * Se a rubrica tem data e algum ascendente não tem, devolve o aviso — e só
     * preenche o ascendente quando o cliente confirma com
     * {@code propagateStartDate}/{@code propagateEndDate}.
     */
    private List<DatePropagationHintDTO> handleDatePropagation(ConstructionBudgetItem item,
                                                               BudgetItemUpsertDTO dto) {
        List<DatePropagationHintDTO> hints = new ArrayList<>();
        boolean propagateStart = Boolean.TRUE.equals(dto.propagateStartDate());
        boolean propagateEnd = Boolean.TRUE.equals(dto.propagateEndDate());

        for (ConstructionBudgetItem ancestor = item.getParent();
             ancestor != null;
             ancestor = ancestor.getParent()) {

            if (item.getStartDate() != null && ancestor.getStartDate() == null) {
                if (propagateStart) {
                    ancestor.setStartDate(item.getStartDate());
                    repository.save(ancestor);
                } else {
                    hints.add(new DatePropagationHintDTO(ancestor.getId(), ancestor.getCode(),
                            ancestor.getName(), "START", item.getStartDate()));
                }
            }

            if (item.getEndDate() != null
                    && (ancestor.getEndDate() == null || ancestor.getEndDate().isBefore(item.getEndDate()))) {
                if (propagateEnd) {
                    ancestor.setEndDate(item.getEndDate());
                    repository.save(ancestor);
                } else {
                    hints.add(new DatePropagationHintDTO(ancestor.getId(), ancestor.getCode(),
                            ancestor.getName(), "END", item.getEndDate()));
                }
            }
        }
        return hints;
    }

    /** Reatribui posições consecutivas aos irmãos depois de uma inserção no meio. */
    private void resequenceSiblings(UUID enterpriseId, UUID parentId, UUID movedId, int targetOrder) {
        List<ConstructionBudgetItem> siblings = new ArrayList<>(
                parentId == null
                        ? repository.findTreeByEnterpriseId(enterpriseId).stream()
                                .filter(i -> i.getParent() == null).toList()
                        : repository.findByParentIdOrderBySortOrderAsc(parentId));

        siblings.sort(Comparator
                .comparingInt((ConstructionBudgetItem i) -> i.getSortOrder() == null ? 0 : i.getSortOrder())
                // em caso de empate, o que acabou de ser movido fica à frente
                .thenComparing(i -> i.getId().equals(movedId) ? 0 : 1));

        int order = 0;
        for (ConstructionBudgetItem sibling : siblings) {
            if (sibling.getSortOrder() == null || sibling.getSortOrder() != order) {
                sibling.setSortOrder(order);
                repository.save(sibling);
            }
            order++;
        }
    }

    private static String blankToNull(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
    }

    /** Acumulador de despesas de uma rubrica. */
    private static final class ExpenseRollup {
        static final ExpenseRollup EMPTY = new ExpenseRollup();

        private BigDecimal total = BigDecimal.ZERO;
        private int count = 0;
        private int missingInvoice = 0;
        private int pendingAccountant = 0;
        private BigDecimal pendingAccountantTotal = BigDecimal.ZERO;

        void add(BigDecimal amount, boolean sentToAccountant, boolean hasInvoice) {
            BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
            total = total.add(value);
            count++;
            if (!hasInvoice) {
                missingInvoice++;
            }
            if (!sentToAccountant) {
                pendingAccountant++;
                pendingAccountantTotal = pendingAccountantTotal.add(value);
            }
        }

        BigDecimal total() { return total; }
        int count() { return count; }
        int missingInvoice() { return missingInvoice; }
        int pendingAccountant() { return pendingAccountant; }
        BigDecimal pendingAccountantTotal() { return pendingAccountantTotal; }
    }
}
