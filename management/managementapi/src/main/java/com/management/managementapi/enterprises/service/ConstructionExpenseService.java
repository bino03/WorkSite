package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.expense.request.ConstructionExpenseUpsertDTO;
import com.management.managementapi.enterprises.dto.expense.response.ConstructionExpenseResponseDTO;
import com.management.managementapi.enterprises.dto.expense.response.InvoiceScanResultDTO;
import com.management.managementapi.enterprises.dto.expense.response.RegisteredInvoiceRefDTO;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionExpenseService {

    private static final Set<String> INVOICE_MIME = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png");
    private static final long MAX_INVOICE_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final String BUCKET = "documents";

    private final ConstructionExpenseRepository repository;
    private final ConstructionBudgetItemRepository budgetItemRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService storageService;
    private final AtInvoiceQrService qrService;
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
                expense.getSupplierNif(),
                expense.getInvoiceNumber(),
                expense.getInvoiceAtcud(),
                expense.getStorageKey() != null,
                resolveInvoiceUrl(expense),
                expense.getOriginalFilename(),
                expense.getMimeType(),
                expense.getSizeBytes(),
                expense.getUploadedBy(),
                resolveProfileName(expense.getUploadedBy()),
                expense.getUploadedAt(),
                expense.isSentToAccountant(),
                expense.getSentToAccountantBy(),
                resolveProfileName(expense.getSentToAccountantBy()),
                resolveProfileRole(expense.getSentToAccountantBy()),
                expense.getSentToAccountantAt(),
                expense.getCreatedBy(),
                resolveProfileName(expense.getCreatedBy()),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }

    public ConstructionExpense create(ConstructionExpenseUpsertDTO dto, MultipartFile invoiceFile) {
        ConstructionBudgetItem item = resolveBudgetItem(dto.budgetItemId());

        ConstructionExpense expense = new ConstructionExpense();
        expense.setBudgetItem(item);
        applyFields(expense, dto);
        authContext.currentProfileId().ifPresent(expense::setCreatedBy);
        // guardar primeiro: a chave de storage precisa do id da despesa
        expense = repository.save(expense);

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            attachInvoice(expense, invoiceFile);
            expense = repository.save(expense);
        }

        return expense;
    }

    public ConstructionExpense update(UUID id, ConstructionExpenseUpsertDTO dto, MultipartFile invoiceFile) {
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

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            deleteInvoiceFromStorage(expense);
            attachInvoice(expense, invoiceFile);
        }

        return repository.save(expense);
    }

    /** Marca (ou desmarca) a fatura como enviada para a contabilidade. */
    public ConstructionExpense setSentToAccountant(UUID id, boolean sent) {
        ConstructionExpense expense = getById(id);

        expense.setSentToAccountant(sent);
        if (sent) {
            expense.setSentToAccountantAt(OffsetDateTime.now());
            authContext.currentProfileId().ifPresent(expense::setSentToAccountantBy);
        } else {
            expense.setSentToAccountantAt(null);
            expense.setSentToAccountantBy(null);
        }

        return repository.save(expense);
    }

    public void delete(UUID id) {
        ConstructionExpense expense = getById(id);
        deleteInvoiceFromStorage(expense);
        repository.delete(expense);
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
        expense.setSupplierNif(dto.supplierNif());
        expense.setInvoiceNumber(dto.invoiceNumber());
        expense.setInvoiceAtcud(dto.invoiceAtcud());
    }

    /**
     * Lê o QR da AT de uma fatura e devolve os campos para preencher o
     * formulário, mais o aviso se a mesma fatura já estiver lançada no projeto.
     *
     * Não grava nada — o utilizador confirma antes de criar a despesa.
     */
    @Transactional(readOnly = true)
    public InvoiceScanResultDTO scanInvoice(UUID enterpriseId, MultipartFile file) {
        validateInvoiceFile(file);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw StorageException.uploadError(file.getOriginalFilename(), e);
        }

        return qrService.read(content, file.getContentType())
                .map(data -> new InvoiceScanResultDTO(
                        true,
                        data.issuerNif(), data.buyerNif(),
                        data.documentType(), data.documentStatus(),
                        data.documentNumber(), data.atcud(),
                        data.invoiceDate(),
                        data.taxableAmount(), data.taxAmount(), data.totalAmount(),
                        findAlreadyRegistered(enterpriseId, data.atcud()),
                        data.warnings()))
                .orElseGet(() -> InvoiceScanResultDTO.notRead(List.of(
                        "Não foi encontrado um QR code da AT legível neste documento. "
                                + "Preencha os campos à mão.")));
    }

    private List<RegisteredInvoiceRefDTO> findAlreadyRegistered(UUID enterpriseId, String atcud) {
        if (atcud == null || atcud.isBlank()) {
            return List.of();
        }
        return repository.findByEnterpriseAndAtcud(enterpriseId, atcud).stream()
                .map(e -> new RegisteredInvoiceRefDTO(
                        e.getId(), e.getName(),
                        e.getBudgetItem().getId(), e.getBudgetItem().getCode(), e.getBudgetItem().getName(),
                        e.getExpenseDate(), e.getTotalPrice()))
                .toList();
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
        return profileRepository.findById(profileId)
                .map(profile -> profile.getName())
                .orElse(null);
    }

    private String resolveProfileRole(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId)
                .map(profile -> profile.getRole() == null ? null : profile.getRole().name())
                .orElse(null);
    }

    private void attachInvoice(ConstructionExpense expense, MultipartFile invoiceFile) {
        validateInvoiceFile(invoiceFile);

        String safeFileName = sanitizeFileName(invoiceFile.getOriginalFilename());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        long timestamp = System.currentTimeMillis();
        String key = String.format("construction-expenses/%s/%s_%d_%s",
                expense.getId(), uniqueId, timestamp, safeFileName);
        String mime = Optional.ofNullable(invoiceFile.getContentType()).orElse("application/octet-stream");

        try (InputStream in = invoiceFile.getInputStream()) {
            storageService.upload(BUCKET, key, mime, in);
        } catch (IOException e) {
            throw StorageException.uploadError(invoiceFile.getOriginalFilename(), e);
        }

        expense.setBucket(BUCKET);
        expense.setStorageKey(key);
        expense.setOriginalFilename(invoiceFile.getOriginalFilename());
        expense.setMimeType(mime);
        expense.setSizeBytes(invoiceFile.getSize());
        expense.setUploadedAt(OffsetDateTime.now());
        authContext.currentProfileId().ifPresent(expense::setUploadedBy);
    }

    private void deleteInvoiceFromStorage(ConstructionExpense expense) {
        if (expense.getStorageKey() == null) {
            return;
        }
        try {
            storageService.delete(expense.getBucket(), expense.getStorageKey());
        } catch (IOException e) {
            log.warn("Não foi possível eliminar a fatura antiga do storage: {}", e.getMessage());
        }
        expense.setBucket(null);
        expense.setStorageKey(null);
        expense.setOriginalFilename(null);
        expense.setMimeType(null);
        expense.setSizeBytes(null);
        expense.setUploadedBy(null);
        expense.setUploadedAt(null);
    }

    private void validateInvoiceFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw FileUploadException.empty(file != null ? file.getOriginalFilename() : "fatura");
        }

        String mime = Optional.ofNullable(file.getContentType()).orElse("");
        if (!INVOICE_MIME.contains(mime)) {
            throw FileUploadException.invalidType(file.getOriginalFilename(), mime, "PDF, JPEG, PNG");
        }

        if (file.getSize() > MAX_INVOICE_BYTES) {
            throw FileUploadException.sizeExceeded(file.getOriginalFilename(), file.getSize(), MAX_INVOICE_BYTES);
        }
    }

    private String resolveInvoiceUrl(ConstructionExpense expense) {
        if (expense.getStorageKey() == null) {
            return null;
        }
        try {
            String key = expense.getStorageKey().startsWith("/")
                    ? expense.getStorageKey().substring(1)
                    : expense.getStorageKey();
            return storageService.createSignedUrl(expense.getBucket(), key, 3600);
        } catch (Exception e) {
            log.warn("Não foi possível gerar signed URL da fatura: {}", e.getMessage());
            return null;
        }
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null) {
            return UUID.randomUUID().toString();
        }
        String normalized = Normalizer.normalize(originalName, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        normalized = normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
        return normalized.replaceAll("_+", "_");
    }
}
