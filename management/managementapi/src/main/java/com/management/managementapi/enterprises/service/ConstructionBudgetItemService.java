package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.request.BudgetItemUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemDeletedDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSearchResultDTO;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /**
     * Procura uma rubrica por código ou por texto — o campo único do ecrã de
     * classificação.
     *
     * Assenta no {@link #getTree} em vez de numa query própria, de propósito:
     * um orçamento são ~200 nós, e é assim que os números da pesquisa
     * (orçamentado, gasto, acima do orçamento) são <b>os mesmos</b> que a página
     * do orçamento mostra. Uma query paralela divergia à primeira mudança na
     * fórmula dos rollups.
     *
     * `HEADING` e `NOTE` ficam de fora: não aceitam despesas, logo não são
     * escolhas possíveis. Um capítulo que seja `ITEM` entra — classificar ao
     * capítulo é legítimo (§7), e o resultado di-lo em {@code chapter}.
     */
    @Transactional(readOnly = true)
    public List<BudgetItemSearchResultDTO> search(UUID enterpriseId, String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<BudgetItemNodeDTO> roots = getTree(enterpriseId).roots();

        // Sem texto, a lista são os capítulos: é por onde se começa a procurar
        // quando não se sabe o código, e um campo vazio sem nada por baixo
        // parecia avariado (2026-09-17).
        if (needle.isEmpty()) {
            List<BudgetItemSearchResultDTO> chapters = new ArrayList<>();
            for (BudgetItemNodeDTO root : roots) {
                if (root.acceptsExpenses()) {
                    chapters.add(toSearchResult(root, label(root)));
                }
            }
            return chapters.size() > limit ? chapters.subList(0, limit) : chapters;
        }

        List<BudgetItemSearchResultDTO> results = new ArrayList<>();
        for (BudgetItemNodeDTO root : roots) {
            collectMatches(root, "", needle, results);
        }
        // O código é uma resposta mais precisa do que o nome: quem escreve "4.2"
        // quer a 4.2, não a primeira rubrica cujo nome por acaso a contenha.
        results.sort(Comparator
                .comparing((BudgetItemSearchResultDTO r) -> !startsWithCode(r.code(), needle))
                .thenComparing(BudgetItemSearchResultDTO::depth)
                .thenComparing(r -> r.code() == null ? "" : r.code()));
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    private void collectMatches(BudgetItemNodeDTO node, String parentPath, String needle,
                                List<BudgetItemSearchResultDTO> out) {
        String path = parentPath.isEmpty() ? label(node) : parentPath + " › " + label(node);

        if (node.acceptsExpenses() && matches(node, needle)) {
            out.add(toSearchResult(node, path));
        }
        // Continua a descer mesmo quando o pai não deu match: a sub-rubrica pode
        // dar, e é ela que interessa.
        node.children().forEach(child -> collectMatches(child, path, needle, out));
    }

    private static String label(BudgetItemNodeDTO node) {
        return node.code() == null ? node.name() : node.code() + " " + node.name();
    }

    private static BudgetItemSearchResultDTO toSearchResult(BudgetItemNodeDTO node, String path) {
        boolean chapter = node.children().stream().anyMatch(BudgetItemNodeDTO::acceptsExpenses);
        return new BudgetItemSearchResultDTO(
                node.id(), node.code(), node.name(), path, node.depth(), chapter,
                node.rolledUpBudget(), node.spentTotal(), node.remaining(), node.overBudget());
    }

    /** Só dígitos e pontos, com ou sem ponto final: "2", "2.", "2.1", "20.1.". */
    private static final Pattern CODE_QUERY = Pattern.compile("\\d+(\\.\\d+)*\\.?");

    /**
     * Uma pesquisa por código é <b>prefixo no mesmo nível</b>: "2" dá a 2 e a 20,
     * não a 2.1 nem a 20.1; "2." dá as filhas da 2; "2.1" dá a 2.1 e a 2.10. É
     * o que "ver sub-rubricas" já assumia ao escrever "2." — com o
     * {@code contains} antigo, "2" trazia a árvore inteira da 2 e da 20 e ainda
     * a 12 (apontado pelo utilizador a 2026-09-21). Texto continua a procurar
     * em toda a árvore, pelo nome ou pelo código.
     */
    private static boolean matches(BudgetItemNodeDTO node, String needle) {
        if (CODE_QUERY.matcher(needle).matches()) {
            return node.code() != null
                    && node.code().startsWith(needle)
                    && depthOfCode(node.code()) == depthOfCode(needle);
        }
        return (node.code() != null && node.code().toLowerCase().contains(needle))
                || node.name().toLowerCase().contains(needle);
    }

    /** Nível de um código pelo número de pontos: "2" → 0, "2." e "2.1" → 1, "2.1.3" → 2. */
    private static int depthOfCode(String code) {
        return (int) code.chars().filter(c -> c == '.').count();
    }

    private static boolean startsWithCode(String code, String needle) {
        return code != null && code.toLowerCase().startsWith(needle);
    }

    /**
     * O {@code budgetTotal} de cada obra — o mesmo número que o cabeçalho da
     * página do orçamento mostra, calculado pela mesma regra de rollup
     * ({@link #rolledUpBudget}). É o que a lista de projetos mostra como
     * "Investimento": o {@code total_investment} escrito à mão na obra divergia
     * do orçamento importado e ninguém sabia qual era o bom.
     *
     * Uma obra sem rubricas não aparece no mapa.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> budgetTotalsByEnterprise(Collection<UUID> enterpriseIds) {
        if (enterpriseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ConstructionBudgetItem>> byEnterprise = new HashMap<>();
        for (ConstructionBudgetItem item : repository.findLiveByEnterpriseIdIn(enterpriseIds)) {
            byEnterprise.computeIfAbsent(item.getEnterprise().getId(), k -> new ArrayList<>()).add(item);
        }

        Map<UUID, BigDecimal> totals = new HashMap<>();
        byEnterprise.forEach((enterpriseId, items) -> {
            Map<UUID, List<ConstructionBudgetItem>> childrenByParent = groupByParent(items);
            BigDecimal total = BigDecimal.ZERO;
            for (ConstructionBudgetItem root : childrenByParent.getOrDefault(null, List.of())) {
                total = total.add(rolledUpBudget(root, childrenByParent));
            }
            totals.put(enterpriseId, total);
        });
        return totals;
    }

    /**
     * A regra de rollup do orçamento, isolada: um nó vale a soma dos filhos se
     * algum descendente tiver preço, senão o seu próprio {@code totalPrice}.
     * É exatamente o que o {@link #buildNode} faz para o {@code rolledUpBudget}
     * — se um dia mudar lá, tem de mudar aqui, senão a lista de projetos e a
     * página do orçamento voltam a mostrar números diferentes.
     */
    private static BigDecimal rolledUpBudget(ConstructionBudgetItem item,
                                             Map<UUID, List<ConstructionBudgetItem>> childrenByParent) {
        BigDecimal childSum = BigDecimal.ZERO;
        boolean childHasPrice = false;
        for (ConstructionBudgetItem child : childrenByParent.getOrDefault(item.getId(), List.of())) {
            BigDecimal childRolledUp = rolledUpBudget(child, childrenByParent);
            childSum = childSum.add(childRolledUp);
            if (childRolledUp.signum() != 0 || child.getTotalPrice() != null) {
                childHasPrice = true;
            }
        }
        BigDecimal own = item.getTotalPrice();
        return childHasPrice ? childSum : (own != null ? own : BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public BudgetTreeDTO getTree(UUID enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND));

        List<ConstructionBudgetItem> items = livesOnly(repository.findTreeByEnterpriseId(enterpriseId));
        Map<UUID, ExpenseRollup> expensesByItem = loadExpenseRollups(enterpriseId);
        Map<UUID, List<ConstructionBudgetItem>> childrenByParent = groupByParent(items);

        List<BudgetItemNodeDTO> roots = new ArrayList<>();
        for (ConstructionBudgetItem root : childrenByParent.getOrDefault(null, List.of())) {
            roots.add(buildNode(root, childrenByParent, expensesByItem, 0, BigDecimal.ZERO));
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
                enterprise.getName(),
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

    /**
     * Um nó com a sua sub-árvore e agregados — usado no GET individual.
     *
     * Constrói a árvore inteira e vai buscar o nó lá dentro, em vez de o construir
     * isolado: o gasto que lhe chega repartido dos pais só se conhece a partir da raiz.
     */
    @Transactional(readOnly = true)
    public BudgetItemNodeDTO getNode(UUID id) {
        ConstructionBudgetItem item = getById(id);
        UUID enterpriseId = item.getEnterprise().getId();

        Map<UUID, List<ConstructionBudgetItem>> childrenByParent =
                groupByParent(livesOnly(repository.findTreeByEnterpriseId(enterpriseId)));
        Map<UUID, ExpenseRollup> expensesByItem = loadExpenseRollups(enterpriseId);

        for (ConstructionBudgetItem root : childrenByParent.getOrDefault(null, List.of())) {
            BudgetItemNodeDTO found = findNode(
                    buildNode(root, childrenByParent, expensesByItem, 0, BigDecimal.ZERO), id);
            if (found != null) {
                return found;
            }
        }
        throw ResourceNotFoundException.budgetItem(id.toString());
    }

    private static BudgetItemNodeDTO findNode(BudgetItemNodeDTO node, UUID id) {
        if (node.id().equals(id)) {
            return node;
        }
        for (BudgetItemNodeDTO child : node.children()) {
            BudgetItemNodeDTO found = findNode(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
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

    /**
     * Elimina a rubrica e toda a sub-árvore — soft delete, não cascata na BD.
     *
     * Bloqueado se houver despesas lançadas em qualquer nó da sub-árvore
     * ({@code BUDGET_013}): mover ou apagar as despesas primeiro é a única
     * forma de esvaziar a rubrica, para nunca se perder uma despesa por engano
     * atrás de uma eliminação. A purga real (hard delete) só acontece 30 dias
     * depois, por job agendado — ver {@code ConstructionBudgetItemPurgeConfig}.
     */
    public void delete(UUID id) {
        ConstructionBudgetItem item = getById(id);
        List<ConstructionBudgetItem> subtree = collectSubtree(item);
        List<UUID> subtreeIds = subtree.stream().map(ConstructionBudgetItem::getId).toList();

        if (expenseRepository.existsByBudgetItemIdIn(subtreeIds)) {
            throw new BusinessException(ErrorCode.BUDGET_ITEM_HAS_EXPENSES);
        }

        OffsetDateTime now = OffsetDateTime.now();
        subtree.forEach(node -> node.setDeletedAt(now));
        repository.saveAll(subtree);
    }

    /** As rubricas eliminadas de um projeto, mais recente primeiro — a zona de recuperação. */
    @Transactional(readOnly = true)
    public List<BudgetItemDeletedDTO> listDeleted(UUID enterpriseId) {
        return repository.findByEnterpriseIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(enterpriseId).stream()
                .map(item -> new BudgetItemDeletedDTO(
                        item.getId(), item.getCode(), item.getName(), item.getRowKind(),
                        item.getDeletedAt(), item.getDeletedAt().plusDays(30)))
                .toList();
    }

    /**
     * Repõe uma rubrica eliminada e a sub-árvore que foi eliminada junto com ela.
     *
     * Volta à mãe original se ela ainda existir e não estiver eliminada; ao
     * topo (sem mãe) caso contrário — a mãe pode ter sido eliminada depois, ou
     * entretanto ter mudado de sítio de um jeito que já não faz sentido. O
     * {@code code}, entretanto livre, pode ter sido reutilizado por outra
     * rubrica — nesse caso {@code BUDGET_DUPLICATE_CODE}, tal como uma
     * criação normal.
     */
    public BudgetItemNodeDTO recover(UUID id) {
        ConstructionBudgetItem item = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.budgetItem(id.toString()));
        if (!item.isDeleted()) {
            throw new BusinessException(ErrorCode.BUDGET_ITEM_NOT_DELETED);
        }

        UUID enterpriseId = item.getEnterprise().getId();
        validateCodeIsFree(enterpriseId, item.getCode(), item.getId());

        ConstructionBudgetItem parent = item.getParent();
        boolean parentRecoverable = parent != null && !parent.isDeleted();
        if (!parentRecoverable) {
            item.setParent(null);
            item.setSortOrder(repository.nextSortOrder(enterpriseId, null));
        }

        List<ConstructionBudgetItem> deletedSubtree = collectDeletedSubtree(item);
        deletedSubtree.forEach(node -> node.setDeletedAt(null));
        repository.saveAll(deletedSubtree);

        return getNode(item.getId());
    }

    /** A rubrica e toda a sua sub-árvore viva — usado para verificar despesas antes de eliminar. */
    private List<ConstructionBudgetItem> collectSubtree(ConstructionBudgetItem root) {
        Map<UUID, List<ConstructionBudgetItem>> childrenByParent =
                groupByParent(livesOnly(repository.findTreeByEnterpriseId(root.getEnterprise().getId())));
        List<ConstructionBudgetItem> out = new ArrayList<>();
        collectSubtreeInto(root, childrenByParent, out);
        return out;
    }

    private void collectSubtreeInto(ConstructionBudgetItem node,
                                    Map<UUID, List<ConstructionBudgetItem>> childrenByParent,
                                    List<ConstructionBudgetItem> out) {
        out.add(node);
        for (ConstructionBudgetItem child : childrenByParent.getOrDefault(node.getId(), List.of())) {
            collectSubtreeInto(child, childrenByParent, out);
        }
    }

    /**
     * A rubrica e a parte da sua sub-árvore que também está eliminada — usado
     * ao recuperar. Descer só por filhos eliminados evita repor algo que
     * nunca chegou a sair (uma sub-rubrica criada depois da eliminação do pai,
     * hipótese hoje impossível pela UI mas não pela BD).
     */
    private List<ConstructionBudgetItem> collectDeletedSubtree(ConstructionBudgetItem root) {
        Map<UUID, List<ConstructionBudgetItem>> childrenByParent = new HashMap<>();
        for (ConstructionBudgetItem candidate : repository.findTreeByEnterpriseId(root.getEnterprise().getId())) {
            if (!candidate.isDeleted() || candidate.getParent() == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(candidate.getParent().getId(), k -> new ArrayList<>()).add(candidate);
        }
        List<ConstructionBudgetItem> out = new ArrayList<>();
        collectSubtreeInto(root, childrenByParent, out);
        return out;
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
            // O documento vive na fatura: sem fatura associada não há nada para
            // enviar ao contabilista, por isso conta como "por enviar" nenhuma
            // das duas coisas.
            boolean hasInvoice = expense.getInvoice() != null;
            boolean sentToAccountant = hasInvoice && expense.getInvoice().isSentToAccountant();

            byItem.computeIfAbsent(expense.getBudgetItem().getId(), k -> new ExpenseRollup())
                    .add(expense.getTotalPrice(), sentToAccountant, hasInvoice);
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
     *
     * O gasto faz o caminho inverso, de cima para baixo: uma despesa lançada
     * numa rubrica com sub-rubricas (15 € na 4.3) aparece repartida em partes
     * iguais por elas (5 € em cada uma de 4.3.1, 4.3.2 e 4.3.3), e assim
     * sucessivamente até às folhas. A despesa continua gravada na 4.3 — só a
     * leitura reparte — e o {@code spentTotal} da 4.3 volta a ser a soma das
     * filhas, por isso nada é contado duas vezes. {@code inherited} é a parte
     * que chega de cima a este nó.
     */
    private BudgetItemNodeDTO buildNode(ConstructionBudgetItem item,
                                        Map<UUID, List<ConstructionBudgetItem>> childrenByParent,
                                        Map<UUID, ExpenseRollup> expensesByItem,
                                        int depth,
                                        BigDecimal inherited) {

        ExpenseRollup ownExpenses = expensesByItem.getOrDefault(item.getId(), ExpenseRollup.EMPTY);
        BigDecimal pool = ownExpenses.total().add(inherited);

        List<ConstructionBudgetItem> childItems = childrenByParent.getOrDefault(item.getId(), List.of());
        Map<UUID, BigDecimal> shares = splitEvenly(pool, childItems, childrenByParent);

        List<BudgetItemNodeDTO> children = new ArrayList<>();
        for (ConstructionBudgetItem child : childItems) {
            children.add(buildNode(child, childrenByParent, expensesByItem, depth + 1,
                    shares.getOrDefault(child.getId(), BigDecimal.ZERO)));
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

        // Se houve por quem repartir, o gasto já está todo nas filhas; senão fica aqui.
        BigDecimal spent = shares.isEmpty() ? childSpent.add(pool) : childSpent;
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
     * Reparte {@code pool} em partes iguais pelas filhas que podem levar gasto —
     * rubricas, ou títulos que agrupem rubricas; as notas ficam de fora.
     *
     * Divide-se a 2 casas e a diferença de arredondamento vai toda para a última
     * filha (10 € por 3 → 3,33 / 3,33 / 3,34), para a soma das partes ser
     * exatamente o {@code pool} — um cêntimo a mais ou a menos por rubrica
     * acumulava ao longo da árvore. Mapa vazio quando não há por quem repartir.
     */
    private Map<UUID, BigDecimal> splitEvenly(BigDecimal pool,
                                              List<ConstructionBudgetItem> children,
                                              Map<UUID, List<ConstructionBudgetItem>> childrenByParent) {
        List<ConstructionBudgetItem> eligible = children.stream()
                .filter(child -> carriesExpenses(child, childrenByParent))
                .toList();
        if (eligible.isEmpty()) {
            return Map.of();
        }

        BigDecimal share = pool.divide(BigDecimal.valueOf(eligible.size()), 2, RoundingMode.HALF_UP);
        Map<UUID, BigDecimal> shares = new HashMap<>();
        BigDecimal distributed = BigDecimal.ZERO;
        for (int i = 0; i < eligible.size() - 1; i++) {
            shares.put(eligible.get(i).getId(), share);
            distributed = distributed.add(share);
        }
        shares.put(eligible.get(eligible.size() - 1).getId(), pool.subtract(distributed));
        return shares;
    }

    /** Uma rubrica, ou um título com pelo menos uma rubrica lá dentro. */
    private boolean carriesExpenses(ConstructionBudgetItem item,
                                    Map<UUID, List<ConstructionBudgetItem>> childrenByParent) {
        if (item.getRowKind().acceptsExpenses()) {
            return true;
        }
        if (item.getRowKind() == BudgetRowKind.NOTE) {
            return false;
        }
        return childrenByParent.getOrDefault(item.getId(), List.of()).stream()
                .anyMatch(child -> carriesExpenses(child, childrenByParent));
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
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_PARENT_NOT_FOUND));
        if (!parent.getEnterprise().getId().equals(enterpriseId)) {
            throw new BusinessException(ErrorCode.BUDGET_PARENT_OTHER_ENTERPRISE);
        }
        return parent;
    }

    /** As rubricas vivas — a árvore visível nunca inclui eliminadas (soft delete). */
    private static List<ConstructionBudgetItem> livesOnly(List<ConstructionBudgetItem> items) {
        return items.stream().filter(i -> !i.isDeleted()).toList();
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

    /**
     * Reatribui posições consecutivas aos irmãos depois de um movimento.
     *
     * {@code targetOrder} é a <b>posição final</b> que a rubrica movida deve
     * ocupar entre os irmãos vivos (0 = primeira): tiram-se-a da lista, e volta
     * a entrar nesse índice. Era um desempate "o movido fica à frente do que
     * tiver o mesmo sortOrder", e isso fazia o "Descer" não mexer: descer um
     * lugar pedia o sortOrder do irmão de baixo, empatava com ele, e o movido
     * ficava outra vez à frente (2026-09-17).
     */
    private void resequenceSiblings(UUID enterpriseId, UUID parentId, UUID movedId, int targetOrder) {
        List<ConstructionBudgetItem> siblings = new ArrayList<>(
                parentId == null
                        ? repository.findTreeByEnterpriseId(enterpriseId).stream()
                                .filter(i -> i.getParent() == null && !i.isDeleted()).toList()
                        : repository.findByParentIdOrderBySortOrderAsc(parentId));

        ConstructionBudgetItem moved = siblings.stream()
                .filter(i -> i.getId().equals(movedId))
                .findFirst()
                .orElse(null);
        siblings.removeIf(i -> i.getId().equals(movedId));
        siblings.sort(Comparator.comparingInt(i -> i.getSortOrder() == null ? 0 : i.getSortOrder()));
        if (moved != null) {
            siblings.add(Math.max(0, Math.min(targetOrder, siblings.size())), moved);
        }

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
