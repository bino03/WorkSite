package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.invoice.request.ConstructionInvoiceUpsertDTO;
import com.management.managementapi.enterprises.dto.invoice.response.BudgetItemSuggestionDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceUploadResultDTO;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.service.ConstructionInvoiceService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Faturas de obra — a caixa de entrada do projeto.
 *
 * Registar e classificar são momentos separados de propósito: {@code POST /}
 * aceita o ficheiro sozinho e {@code PATCH /{id}/allocate} é que decide a
 * rubrica, mais tarde. Ver {@link ConstructionInvoiceService}.
 */
@RestController
@RequestMapping("/construction-invoices")
@RequiredArgsConstructor
public class ConstructionInvoiceController {

    private final ConstructionInvoiceService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    /**
     * Carrega uma fatura. O cliente chama isto uma vez por ficheiro largado, em
     * paralelo — não há endpoint de lote de propósito: assim cada ficheiro tem o
     * seu resultado e um QR ilegível não estraga os restantes.
     *
     * @param originalSizeBytes tamanho do ficheiro antes da compressão feita no
     *                          browser; só para se poder mostrar a poupança
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> upload(
            @RequestParam UUID enterpriseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long originalSizeBytes,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.upload(enterpriseId, file, originalSizeBytes);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, result.invoice().id(),
                        invoiceLabel(result.invoice()), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * A caixa de entrada. Sem filtros devolve tudo; o cliente abre-a com
     * {@code allocated=false}, que é o trabalho por fazer.
     */
    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Page<ConstructionInvoiceResponseDTO> listByEnterprise(
            @PathVariable UUID enterpriseId,
            @RequestParam(required = false) Boolean allocated,
            @RequestParam(required = false) Boolean needsReview,
            @RequestParam(required = false) Boolean sentToAccountant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "uploadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(enterpriseId, allocated, needsReview, sentToAccountant, from, to, q, pageable);
    }

    /** Quantas faturas estão por associar — alimenta o aviso no ecrã do orçamento. */
    @GetMapping("/enterprise/{enterpriseId}/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Long> countPending(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.countPending(enterpriseId));
    }

    /**
     * Rubrica sugerida para as faturas deste fornecedor, com base no que já foi
     * lançado neste projeto. Devolve 204 quando ainda não há histórico.
     */
    @GetMapping("/enterprise/{enterpriseId}/suggestion")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetItemSuggestionDTO> suggestion(
            @PathVariable UUID enterpriseId,
            @RequestParam String supplierNif) {
        return service.suggestBudgetItem(enterpriseId, supplierNif)
                .map(item -> new BudgetItemSuggestionDTO(item.getId(), item.getCode(), item.getName()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Detalhe. É a única resposta que traz o link assinado do documento completo. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getDetail(id));
    }

    /** Corrige à mão o que o QR não trouxe ou trouxe errado. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody ConstructionInvoiceUpsertDTO dto,
            HttpServletRequest request) {
        ConstructionInvoice updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, updated.getInvoiceNumber(), null, request));

        return ResponseEntity.ok(service.toResponseDTO(updated, true));
    }

    /** Substitui o ficheiro — relê o QR e regenera a miniatura. */
    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> replaceFile(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.replaceFile(id, file);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoiceLabel(result.invoice()), null, request));

        return ResponseEntity.ok(result);
    }

    /**
     * Relê o QR do documento já arquivado e repõe os campos fiscais como a AT os
     * declarou — o desfazer de uma correção manual feita por engano.
     *
     * Não recebe ficheiro: usa o que já está no Storage. Para trocar a
     * digitalização é {@code POST /{id}/file}. O nome do fornecedor e as notas
     * não são tocados, porque não vêm do QR.
     */
    @PostMapping("/{id}/rescan")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> rescan(
            @PathVariable UUID id,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.rescan(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoiceLabel(result.invoice()), null, request));

        return ResponseEntity.ok(result);
    }

    /** Liga a fatura a uma rubrica, criando o lançamento. */
    @PatchMapping("/{id}/allocate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> allocate(
            @PathVariable UUID id,
            @RequestParam UUID budgetItemId,
            HttpServletRequest request) {
        ConstructionExpense expense = service.allocate(id, budgetItemId);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, expense.getId(), expense.getName(), request));

        return ResponseEntity.ok(service.getDetail(id));
    }

    /** Devolve a fatura à caixa de entrada, apagando o lançamento. */
    @DeleteMapping("/{id}/allocate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> deallocate(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ConstructionInvoice invoice = service.deallocate(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoice.getInvoiceNumber(), null, request));

        return ResponseEntity.ok(service.getDetail(id));
    }

    /** Marca/desmarca a fatura como enviada para o contabilista. */
    @PatchMapping("/{id}/accountant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> setSentToAccountant(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean sent,
            HttpServletRequest request) {
        ConstructionInvoice updated = service.setSentToAccountant(id, sent);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, updated.getInvoiceNumber(), null, request));

        return ResponseEntity.ok(service.toResponseDTO(updated, false));
    }

    /** Apaga a fatura, o ficheiro, a miniatura e o lançamento que dela nasceu. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionInvoice invoice = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoice.getInvoiceNumber(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** O registo de atividade precisa de algo legível mesmo quando o QR falhou. */
    private String invoiceLabel(ConstructionInvoiceResponseDTO invoice) {
        if (invoice.invoiceNumber() != null && !invoice.invoiceNumber().isBlank()) {
            return invoice.invoiceNumber();
        }
        return invoice.originalFilename();
    }
}
