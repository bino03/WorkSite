package com.management.managementapi.service.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.management.managementapi.dto.employee.*;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {
    EmployeeResponseDTO getById(UUID id);

    // Apenas declaração - sem implementação
    Page<EmployeeResponseDTO> search(String q, String role, String status, Pageable pageable, String createdAtSortDir);

    /**
     * Lista leve (id, nome, email, role) para pickers — ex. atribuir uma tarefa a um utilizador.
     * Não pagina nem expõe telefone/foto/estado/timestamps.
     */
    List<EmployeeAssignableDTO> listAssignable();

    EmployeeResponseDTO update(UUID id, EmployeeUpdateRequestDTO dto);

    EmployeeResponseDTO updateRole(UUID id, EmployeeRolePatchRequestDTO dto);

    EmployeeResponseDTO updateAvatar(UUID id, EmployeeAvatarUpdateRequestDTO dto);

    List<EmployeeResponseDTO> getDeletedProfiles();

    EmployeeResponseDTO restoreProfile(UUID id);

    EmployeeResponseDTO blockProfile(UUID id);

    EmployeeResponseDTO unblockProfile(UUID id);

    void deleteProfile(UUID id);
}
