package com.management.managementapi.controller;
// TRATAMENTOS DE ERROS ✅✅

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.profile.EmailUpdateRequest;
import com.management.managementapi.dto.profile.PasswordUpdateRequest;
import com.management.managementapi.dto.profile.ProfileDTO;
import com.management.managementapi.dto.profile.ProfileListResponseDTO;
import com.management.managementapi.dto.profile.ProfileUpdateRequest;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.service.ProfileService;

import jakarta.validation.Valid;
import jakarta.validation.ValidationException;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final JdbcTemplate jdbcTemplate;

    public ProfileController(ProfileService profileService, JdbcTemplate jdbcTemplate) {
        this.profileService = profileService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===================== PERFIL (já tinhas) =====================

/**
     * GET /api/profiles/agents
     * Retorna todos os profiles exceto o do utilizador autenticado
     */
    @GetMapping("/agents")
    public ResponseEntity<List<ProfileListResponseDTO>> getAllAgents(Authentication authentication) {
        // Obter o auth_user_id do utilizador autenticado (vem do JWT sub)
        UUID currentAuthUserId = UUID.fromString(authentication.getName());
        
        String sql = """
            SELECT 
                p.id,
                p.name,
                COALESCE(u.email, '') as email,
                p.role,
                p.phone_number
            FROM worksite.profile p
            LEFT JOIN auth.users u ON p.auth_user_id = u.id
            WHERE p.auth_user_id != ? OR p.auth_user_id IS NULL
            ORDER BY p.name ASC
        """;
        
        List<ProfileListResponseDTO> profiles = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
                ProfileListResponseDTO dto = new ProfileListResponseDTO();
                dto.setId(UUID.fromString(rs.getString("id")));
                dto.setName(rs.getString("name"));
                dto.setEmail(rs.getString("email"));
                
                // ✅ CONVERTER STRING PARA ENUM
                String roleString = rs.getString("role");
                if (roleString != null && !roleString.isBlank()) {
                    try {
                        dto.setRole(ProfileRole.valueOf(roleString));
                    } catch (IllegalArgumentException e) {
                        // Se o valor não for válido, usar um default ou null
                        dto.setRole(null);
                    }
                }
                
                dto.setPhoneNumber(rs.getString("phone_number"));
                return dto;
            },
            currentAuthUserId
        );

        return ResponseEntity.ok(profiles);
    }





    @GetMapping("/myprofile")
    public ResponseEntity<ProfileDTO> getMyProfile(Authentication authentication) {
        UUID authUserId = UUID.fromString(authentication.getName());
        ProfileDTO profile = profileService.getProfileByUserId(authUserId)
                .orElseThrow(() -> ResourceNotFoundException.profile(authUserId.toString()));
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/updateNamePhone")
    public ResponseEntity<String> updateNamePhone(@RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        UUID authUserId = UUID.fromString(authentication.getName());
        try {
            profileService.updateNamePhone(authUserId, request);
            return ResponseEntity.ok("Perfil atualizado com sucesso.");
        } catch (ValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    @PutMapping("/updateEmail")
    public ResponseEntity<String> updateEmail(@RequestBody EmailUpdateRequest request,
            Authentication authentication) {
        UUID authUserId = Objects.requireNonNull(UUID.fromString(authentication.getName()), "authUserId");
        try {
            profileService.updateEmail(authUserId, request);
            return ResponseEntity.ok("Email atualizado com sucesso.");
        } catch (ValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

   @PutMapping("/updatePassword")
public ResponseEntity<String> updatePassword(@RequestBody @Valid PasswordUpdateRequest request,
                                             Authentication authentication) {
    UUID authUserId = Objects.requireNonNull(UUID.fromString(authentication.getName()), "authUserId");
        try {
            profileService.updatePassword(authUserId, request);
            return ResponseEntity.ok("Palavra-passe atualizada com sucesso.");
        } catch (ValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

}
