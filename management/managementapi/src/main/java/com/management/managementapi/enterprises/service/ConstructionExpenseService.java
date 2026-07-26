package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.expense.ConstructionExpenseResponseDTO;
import com.management.managementapi.enterprises.dto.expense.CreateConstructionExpenseDTO;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionSubStage;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionSubStageRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
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
    private final ConstructionSubStageRepository subStageRepository;
    private final SupabaseStorageService storageService;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<ConstructionExpenseResponseDTO> listBySubStage(UUID subStageId) {
        return repository.findBySubStageId(subStageId).stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConstructionExpense getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.constructionExpense(id.toString()));
    }

    @Transactional(readOnly = true)
    public ConstructionExpenseResponseDTO toResponseDTO(ConstructionExpense expense) {
        return new ConstructionExpenseResponseDTO(
                expense.getId(),
                expense.getName(),
                expense.getPrice(),
                expense.getSubStage().getId(),
                resolveInvoiceUrl(expense),
                expense.getOriginalFilename(),
                expense.getMimeType(),
                expense.getSizeBytes(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }

    public ConstructionExpense create(CreateConstructionExpenseDTO dto, MultipartFile invoiceFile) {
        ConstructionSubStage subStage = subStageRepository.findById(dto.subStageId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_SUBSTAGE_NOT_FOUND));

        ConstructionExpense expense = new ConstructionExpense();
        expense.setName(dto.name());
        expense.setPrice(dto.price());
        expense.setSubStage(subStage);
        authContext.currentProfileId().ifPresent(expense::setCreatedBy);
        expense = repository.save(expense);

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            attachInvoice(expense, invoiceFile);
            expense = repository.save(expense);
        }

        return expense;
    }

    public ConstructionExpense update(UUID id, CreateConstructionExpenseDTO dto, MultipartFile invoiceFile) {
        ConstructionExpense expense = getById(id);

        if (!expense.getSubStage().getId().equals(dto.subStageId())) {
            ConstructionSubStage subStage = subStageRepository.findById(dto.subStageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_SUBSTAGE_NOT_FOUND));
            expense.setSubStage(subStage);
        }

        expense.setName(dto.name());
        expense.setPrice(dto.price());

        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            deleteInvoiceFromStorage(expense);
            attachInvoice(expense, invoiceFile);
        }

        return repository.save(expense);
    }

    public void delete(UUID id) {
        ConstructionExpense expense = getById(id);
        deleteInvoiceFromStorage(expense);
        repository.delete(expense);
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
