package com.management.managementapi.service;

import com.management.managementapi.dto.auth.SupabaseAuthResponse;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.model.PendingInvite;
import com.management.managementapi.model.Profile;
import com.management.managementapi.repository.PendingInviteRepository;
import com.management.managementapi.repository.ProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Aceitação de um convite: o outro extremo do `POST /auth/admin/invite`.
 *
 * Esta metade nunca chegou a ser escrita. O DTO {@code AcceptInviteRequest} existia,
 * o path estava em {@code permitAll} no {@code SecurityConfig} e o
 * {@code AcceptInvitePage} do Backoffice chamava-o — mas nenhum controller o
 * implementava, e o convite morria num 404 no último passo.
 *
 * <b>Endpoint público, e por isso conservador nas mensagens</b>: quem chega aqui não
 * está autenticado. As respostas dizem o que se passa com <i>o convite</i> (não
 * encontrado, já usado, expirado) e nunca se o email já tem conta — isso sai do
 * Supabase, no fim, e só depois de um token válido ter sido apresentado.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InviteService {

    private final PendingInviteRepository pendingInviteRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseAuthService supabaseAuthService;

    /**
     * O utilizador no Supabase é criado <b>antes</b> do perfil, e a criação não é
     * transacional (é uma chamada HTTP). Se o {@code save} do perfil falhar, fica um
     * utilizador de auth sem perfil — visível no dashboard do Supabase e recuperável à
     * mão. A alternativa (criar o perfil primeiro) deixaria um perfil sem conta, que é
     * pior: aparece nas listas do Backoffice como se fosse gente.
     */
    @Transactional
    public void accept(String token, String password, String name) {
        PendingInvite invite = pendingInviteRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_TOKEN_INVALID,
                        "Convite não encontrado. Confirma o link que recebeste por email."));

        switch (invite.getStatus()) {
            case ACCEPTED -> throw new BusinessException(ErrorCode.USER_TOKEN_INVALID,
                    "Este convite já foi utilizado. Se a conta é tua, entra com o teu email e password.");
            case CANCELLED -> throw new BusinessException(ErrorCode.USER_TOKEN_INVALID,
                    "Este convite foi cancelado. Pede um novo a quem te convidou.");
            case EXPIRED -> throw expired(invite);
            case PENDING -> { /* o único caminho que segue */ }
        }

        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw expired(invite);
        }

        SupabaseAuthResponse.SupabaseUser user =
                supabaseAuthService.createUser(invite.getEmail(), password);

        Profile profile = new Profile();
        profile.setAuthUserId(UUID.fromString(user.getId()));
        profile.setName(name.trim());
        profile.setPhoneNumber(invite.getPhone());
        profile.setRole(invite.getRole());
        profileRepository.save(profile);

        invite.setStatus(PendingInvite.InviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        pendingInviteRepository.save(invite);

        log.info("Convite aceite por {} ({})", invite.getEmail(), profile.getRole());
    }

    /**
     * Marca o convite como expirado ao mesmo tempo que recusa. Sem isto, um convite
     * caducado ficava `PENDING` para sempre na lista de convites e continuava a
     * bloquear o reenvio para o mesmo email (`existsByEmailAndStatus`).
     */
    private BusinessException expired(PendingInvite invite) {
        if (invite.getStatus() != PendingInvite.InviteStatus.EXPIRED) {
            invite.setStatus(PendingInvite.InviteStatus.EXPIRED);
            pendingInviteRepository.save(invite);
        }
        return new BusinessException(ErrorCode.USER_TOKEN_EXPIRED,
                "Este convite expirou. Pede um novo a quem te convidou.");
    }
}
