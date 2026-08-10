package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.expense.request.ConstructionExpenseUpsertDTO;
import com.management.managementapi.enterprises.dto.expense.response.ConstructionExpenseResponseDTO;
import com.management.managementapi.enterprises.dto.expense.response.ExpenseInvoiceRefDTO;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.model.Profile;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Despesas de obra — a afetação de um gasto a uma rubrica do orçamento.
 *
 * O <b>documento</b> não passa por aqui: ficheiro, QR da AT e envio para a
 * contabilidade vivem em {@link ConstructionInvoiceService}. Esta classe trata
 * de lançamentos feitos à mão, sem fatura; os que nascem de uma fatura são
 * criados por {@code ConstructionInvoiceService.allocate}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionExpenseService {

    private final ConstructionExpenseRepository repository;
    private final ConstructionBudgetItemRepository budgetItemRepository;
    private final ProfileRepository profileRepository;
    private final SignedUrlService signedUrls;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<ConstructionExpenseResponseDTO> listByBudgetItem(UUID budgetItemId) {
        return repository.findByBudgetItemIdOrderByCreatedAtDesc(budgetItemId).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    /** Lista plana do projeto inteiro, com filtros — ver o Javadoc do repositório. */
    @Transactional(readOnly = true)
    public Page<ConstructionExpenseResponseDTO> search(UUID enterpriseId, LocalDate from, LocalDate to,
                                                       Boolean sentToAccountant, Boolean hasInvoice,
                                                       String q, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return repository.search(enterpriseId, from, to, sentToAccountant, hasInvoice, query, pageable)
                .map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public ConstructionExpense getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.constructionExpense(id.toString()));
    }

    @Transactional(readOnly = true)
    public ConstructionExpenseResponseDTO toResponseDTO(ConstructionExpense expense) {
        ConstructionBudgetItem item = expense.getBudgetItem();
        return new ConstructionExpenseResponseDTO(
                expense.getId(),
                item.getId(),
                item.getCode(),
                item.getName(),
                expense.getName(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getUnit(),
                expense.getQuantity(),
                expense.getUnitPrice(),
                expense.getTotalPrice(),
                expense.getObservations(),
                toInvoiceRef(expense.getInvoice()),
                expense.getCreatedBy(),
                resolveProfileName(expense.getCreatedBy()),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }

    private ExpenseInvoiceRefDTO toInvoiceRef(ConstructionInvoice invoice) {
        if (invoice == null) {
            return null;
        }
        return new ExpenseInvoiceRefDTO(
                invoice.getId(),
                invoice.getSupplierName(),
                invoice.getSupplierNif(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceAtcud(),
                invoice.getInvoiceDate(),
                signedUrls.resolve(invoice.getBucket(), invoice.getThumbnailKey()),
                invoice.getOriginalFilename(),
                invoice.getMimeType(),
                invoice.getSizeBytes(),
                invoice.isSentToAccountant(),
                resolveProfileName(invoice.getSentToAccountantBy()),
                resolveProfileRole(invoice.getSentToAccountantBy()),
                invoice.getSentToAccountantAt());
    }

    /**
     * Lançamento feito à mão, sem documento. Uma despesa com fatura entra pela
     * caixa de entrada — ver {@code ConstructionInvoiceService.allocate}.
     */
    public ConstructionExpense create(ConstructionExpenseUpsertDTO dto) {
        ConstructionExpense expense = new ConstructionExpense();
        expense.setBudgetItem(resolveBudgetItem(dto.budgetItemId()));
        applyFields(expense, dto);
        authContext.currentProfileId().ifPresent(expense::setCreatedBy);

        return repository.save(expense);
    }

    public ConstructionExpense update(UUID id, ConstructionExpenseUpsertDTO dto) {
        ConstructionExpense expense = getById(id);

        if (!expense.getBudgetItem().getId().equals(dto.budgetItemId())) {
            ConstructionBudgetItem target = resolveBudgetItem(dto.budgetItemId());
            // Mudar de rubrica é legítimo; mudar de projeto não. Sem esta
            // verificação, um PUT com o id de uma rubrica de outro projeto
            // levava a despesa para lá em silêncio, estragando os totais dos dois.
            if (!target.getEnterprise().getId().equals(expense.getBudgetItem().getEnterprise().getId())) {
                throw new BusinessException(ErrorCode.EXPENSE_ITEM_OTHER_ENTERPRISE);
            }
            expense.setBudgetItem(target);
        }

        applyFields(expense, dto);

        return repository.save(expense);
    }

    public void delete(UUID id) {
        // A fatura fica: apagar o lançamento devolve-a à caixa de entrada, que é
        // o que se quer quando a classificação estava errada.
        repository.delete(getById(id));
    }

    // ── auxiliares ────────────────────────────────────────────

    private void applyFields(ConstructionExpense expense, ConstructionExpenseUpsertDTO dto) {
        expense.setName(dto.name());
        expense.setDescription(dto.description());
        expense.setExpenseDate(dto.expenseDate());
        expense.setUnit(dto.unit());
        expense.setQuantity(dto.quantity());
        expense.setUnitPrice(dto.unitPrice());
        expense.setTotalPrice(dto.totalPrice());
        expense.setObservations(dto.observations());
    }

    private ConstructionBudgetItem resolveBudgetItem(UUID budgetItemId) {
        ConstructionBudgetItem item = budgetItemRepository.findById(budgetItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_BUDGET_ITEM_NOT_FOUND));
        if (!item.getRowKind().acceptsExpenses()) {
            throw new BusinessException(ErrorCode.EXPENSE_ITEM_NOT_EXPENSABLE);
        }
        return item;
    }

    private String resolveProfileName(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId).map(Profile::getName).orElse(null);
    }

    private String resolveProfileRole(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId)
                .map(profile -> profile.getRole() == null ? null : profile.getRole().name())
                .orElse(null);
    }
}
