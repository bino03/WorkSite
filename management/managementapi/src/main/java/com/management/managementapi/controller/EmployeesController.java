package com.management.managementapi.controller;

import com.management.managementapi.dto.employee.*;
import com.management.managementapi.service.employee.EmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/employees", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class EmployeesController {

    private final EmployeeService service;

    public EmployeesController(EmployeeService service) {
        this.service = service;
    }

    @Operation(summary = "Detalhe de funcionário por ID")
    @ApiResponse(responseCode = "200", description = "OK")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @GetMapping("/{id}")
    public EmployeeResponseDTO getById(
            @Parameter(description = "ID do funcionário (profile.id)")
            @PathVariable UUID id
    ) {
        return service.getById(id);
    }

    @Operation(summary = "Lista leve de utilizadores para pickers (ex.: atribuir tarefa)",
            description = "Devolve só id, nome, email e role — sem telefone, foto, estado ou timestamps.")
    @ApiResponse(responseCode = "200", description = "OK")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @GetMapping("/assignable")
    public List<EmployeeAssignableDTO> listAssignable() {
        return service.listAssignable();
    }

    @Operation(summary = "Listar funcionários com filtros e paginação",
            description = """
                Pesquisa por `q` em nome, email ou telefone.
                Filtros: `role`, `status`.
                Ordenação: **apenas por created_at** (`sortDir` = asc|desc).
                """)
    @ApiResponse(responseCode = "200", description = "OK")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @GetMapping
    public Page<EmployeeResponseDTO> search(
            @Parameter(description = "Pesquisa livre (nome, email, telefone)") @RequestParam(required = false) String q,
            @Parameter(description = "Role (texto do enum)") @RequestParam(required = false) String role,
            @Parameter(description = "Status (texto do enum)") @RequestParam(required = false) String status,
            @Parameter(description = "Página (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Direção de ordenação por created_at (asc|desc)") @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Pageable pageable = PageRequest.of(page, size); // ordenação controlada no SQL (created_at)
        return service.search(q, role, status, pageable, sortDir);
    }

    @Operation(summary = "Editar funcionário (PUT)")
    @ApiResponse(responseCode = "200", description = "Atualizado")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public EmployeeResponseDTO update(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeUpdateRequestDTO body
    ) {
        return service.update(id, body);
    }

    @Operation(summary = "Eliminar conta de funcionário — soft delete (DELETE)")
    @ApiResponse(responseCode = "204", description = "Eliminado")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable UUID id) {
        service.deleteProfile(id);
    }

    @Operation(summary = "Bloquear conta de funcionário (PATCH)")
    @ApiResponse(responseCode = "200", description = "Conta bloqueada")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/block")
    public EmployeeResponseDTO blockProfile(@PathVariable UUID id) {
        return service.blockProfile(id);
    }

    @Operation(summary = "Desbloquear conta de funcionário (PATCH)")
    @ApiResponse(responseCode = "200", description = "Conta desbloqueada")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/unblock")
    public EmployeeResponseDTO unblockProfile(@PathVariable UUID id) {
        return service.unblockProfile(id);
    }

    @Operation(summary = "Mudar role do funcionário (PATCH)")
    @ApiResponse(responseCode = "200", description = "Atualizado")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/role")
    public EmployeeResponseDTO patchRole(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeRolePatchRequestDTO body
    ) {
        return service.updateRole(id, body);
    }

    @Operation(summary = "Atualizar avatarUrl (metadados)")
    @ApiResponse(responseCode = "200", description = "Atualizado")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/avatar")
    public EmployeeResponseDTO updateAvatar(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeAvatarUpdateRequestDTO body
    ) {
        return service.updateAvatar(id, body);
    }
}
