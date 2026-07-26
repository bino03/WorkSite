package com.management.managementapi.controller;

import com.management.managementapi.dto.admin.CreateEmployeeRequest;
import com.management.managementapi.dto.admin.EmployeeResponseDTO;
import com.management.managementapi.dto.admin.InviteResponseDTO;
import com.management.managementapi.dto.admin.SendInviteRequest;
import com.management.managementapi.dto.auth.SupabaseAuthResponse;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.model.PendingInvite;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.repository.PendingInviteRepository;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.SupabaseAuthService;
import com.management.managementapi.service.email.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuthController {

    private final SupabaseAuthService supabaseAuthService;
    private final ProfileRepository profileRepository;
    private final PendingInviteRepository pendingInviteRepository;
    private final EmailService emailService;
    private final AuthContext authContext;

    /**
     * Cria utilizador diretamente: regista no Supabase + cria perfil na BD.
     */
    @PostMapping("/create")
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest req) {

        SupabaseAuthResponse.SupabaseUser supabaseUser =
                supabaseAuthService.createUser(req.getEmail(), req.getPassword());

        UUID authUserId = UUID.fromString(supabaseUser.getId());

        Profile profile = new Profile();
        profile.setAuthUserId(authUserId);
        profile.setName(req.getName());
        profile.setPhoneNumber(req.getPhone());
        profile.setRole(req.getRole());
        profile = profileRepository.save(profile);

        log.info("Employee created: {} ({})", profile.getName(), authUserId);

        EmployeeResponseDTO response = EmployeeResponseDTO.builder()
                .id(profile.getId())
                .authUserId(profile.getAuthUserId())
                .name(profile.getName())
                .email(supabaseUser.getEmail())
                .phoneNumber(profile.getPhoneNumber())
                .photoUrl(null)
                .role(profile.getRole().name())
                .status(profile.getAccountStatus().name())
                .createdAt(profile.getCreatedAt() != null ? profile.getCreatedAt().toInstant() : Instant.now())
                .updatedAt(profile.getUpdatedAt() != null ? profile.getUpdatedAt().toInstant() : Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Envia convite por email (cria registo em settings.pending_invites + envia email).
     */
    @PostMapping("/invite")
    public ResponseEntity<Void> sendInvite(@Valid @RequestBody SendInviteRequest req) {

        if (pendingInviteRepository.existsByEmailAndStatus(req.getEmail(), PendingInvite.InviteStatus.PENDING)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS,
                    "Já existe um convite pendente para este email");
        }

        UUID invitedById = authContext.currentProfileId()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        String invitedByName = profileRepository.findNameOnlyById(invitedById)
                .orElse("Admin");

        String token = UUID.randomUUID().toString();

        PendingInvite invite = new PendingInvite();
        invite.setEmail(req.getEmail());
        invite.setPhone(req.getPhone());
        invite.setRole(req.getRole());
        invite.setInviteToken(token);
        invite.setInvitedBy(invitedById);
        invite.setSentAt(Instant.now());
        invite.setExpiresAt(Instant.now().plusSeconds(86400)); // 24h
        pendingInviteRepository.save(invite);

        emailService.sendInviteEmail(req.getEmail(), token, invitedByName);

        log.info("Invite sent to {} by {}", req.getEmail(), invitedByName);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lista convites com filtros opcionais de status e role.
     */
    @GetMapping("/invites")
    public ResponseEntity<List<InviteResponseDTO>> listInvites(
            @RequestParam(required = false) PendingInvite.InviteStatus status,
            @RequestParam(required = false) ProfileRole role) {

        List<PendingInvite> invites = pendingInviteRepository.findAllWithFilters(status, role);

        List<InviteResponseDTO> result = invites.stream().map(invite -> {
            String invitedByName = profileRepository.findNameOnlyById(invite.getInvitedBy())
                    .orElse(invite.getInvitedBy().toString());

            return InviteResponseDTO.builder()
                    .id(invite.getId())
                    .email(invite.getEmail())
                    .phone(invite.getPhone())
                    .role(invite.getRole().name())
                    .status(invite.getStatus().name())
                    .sentAt(invite.getSentAt())
                    .acceptedAt(invite.getAcceptedAt())
                    .expiresAt(invite.getExpiresAt())
                    .invitedBy(invite.getInvitedBy().toString())
                    .invitedByName(invitedByName)
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }
}
