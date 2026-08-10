package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.expense.request.ConstructionExpenseUpsertDTO;
import com.management.managementapi.enterprises.dto.expense.response.ConstructionExpenseResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.service.ConstructionExpenseService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Despesas lançadas contra rubricas do orçamento.
 *
 * Deixou de haver upload aqui: o ficheiro, a leitura do QR e o envio para a
 * contabilidade passaram para {@code /construction-invoices}, porque a fatura
 * existe antes de se saber a que rubrica pertence. O que resta neste
 * controlador são os lançamentos feitos à mão, sem documento.
 */
@RestController
@RequestMapping("/construction-expenses")
@RequiredArgsConstructor
public class ConstructionExpenseController {

    private final ConstructionExpenseService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    @GetMapping("/budget-item/{budgetItemId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<List<ConstructionExpenseResponseDTO>> listByBudgetItem(@PathVariable UUID budgetItemId) {
        return ResponseEntity.ok(service.listByBudgetItem(budgetItemId));
    }

    /**
     * Lista plana das despesas do projeto, atravessando a árvore toda.
     *
     * É a vista de quem trata da contabilidade: "o que falta enviar", "as
     * faturas de setembro", "o que está lançado sem documento". Todos os
     * filtros são opcionais.
     */
    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Page<ConstructionExpenseResponseDTO> listByEnterprise(
            @PathVariable UUID enterpriseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean sentToAccountant,
            @RequestParam(required = false) Boolean hasInvoice,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(enterpriseId, from, to, sentToAccountant, hasInvoice, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionExpenseResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.toResponseDTO(service.getById(id)));
    }

    /**
     * Lançamento sem documento. Com fatura, o caminho é
     * {@code POST /construction-invoices} seguido de
     * {@code PATCH /construction-invoices/{id}/allocate}.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionExpenseResponseDTO> create(
            @Valid @RequestBody ConstructionExpenseUpsertDTO dto,
            HttpServletRequest request) {
        ConstructionExpense created = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(service.toResponseDTO(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionExpenseResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody ConstructionExpenseUpsertDTO dto,
            HttpServletRequest request) {
        ConstructionExpense updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, id, updated.getName(), null, request));

        return ResponseEntity.ok(service.toResponseDTO(updated));
    }

    /**
     * Apaga o lançamento. A fatura, se existir, volta à caixa de entrada — é o
     * que se quer quando a classificação estava errada. Para eliminar também o
     * documento, {@code DELETE /construction-invoices/{id}}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionExpense expense = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, id, expense.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
