package com.management.managementapi.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.model.PasswordResetToken;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.User;
import com.management.managementapi.repository.PasswordResetTokenRepository;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.UserRepository;
import com.management.managementapi.service.email.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Recuperação de password com fluxo próprio.
 *
 * A página `/forgot-password` existia desde o início e mentia: mostrava "email
 * enviado" sem chamar API nenhuma, e não havia endpoint do lado do backend.
 *
 * Fluxo próprio e não o `/auth/v1/recover` do Supabase para o email sair pelo SMTP
 * de `settings.email_providers` — o mesmo do convite — em vez do do dashboard do
 * Supabase, cujo template não se controla a partir daqui e cujo SMTP gratuito tem
 * limites de rate apertados.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    /**
     * Uma hora. Quem tem o link entra na conta sem saber a password antiga, por isso
     * a janela é bem mais curta que as 24h do convite. O texto do email diz o mesmo
     * número — mudar aqui obriga a mudar lá.
     */
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseAuthService supabaseAuthService;
    private final EmailService emailService;

    /**
     * Pede um link de recuperação.
     *
     * <b>Nunca diz se o email existe.</b> Quem chama não está autenticado, e uma
     * resposta diferente para email conhecido e desconhecido transformava este
     * endpoint num verificador de contas. Sai sempre igual; o que varia é só se um
     * email chega ou não.
     *
     * Sem limite de tentativas por enquanto — vale a pena quando isto estiver
     * exposto fora da rede interna.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim();

        Optional<User> user = userRepository.findByEmailIgnoreCase(email);
        if (user.isEmpty()) {
            log.info("Pedido de recuperação para email sem conta: {}", email);
            return;
        }

        UUID authUserId = user.get().getId();

        // Só utilizadores internos: uma conta de auth sem perfil não tem nada para
        // aceder no Backoffice, e pode ser um resto de um convite que falhou a meio.
        if (profileRepository.findByAuthUserId(authUserId).isEmpty()) {
            log.warn("Pedido de recuperação para conta sem perfil: {}", email);
            return;
        }

        tokenRepository.invalidatePending(authUserId, Instant.now());

        PasswordResetToken reset = new PasswordResetToken();
        reset.setAuthUserId(authUserId);
        reset.setEmail(user.get().getEmail());
        reset.setToken(UUID.randomUUID().toString());
        reset.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        tokenRepository.save(reset);

        emailService.sendPasswordResetEmail(reset.getEmail(), reset.getToken());
    }

    /**
     * Define a password nova.
     *
     * Depois de gravar, marca `lastTokenResetAt` no perfil: o `AccountLockFilter`
     * recusa qualquer JWT emitido antes desse instante, o que faz cair as sessões
     * abertas noutros sítios. É o comportamento que se espera de uma recuperação —
     * quem a pediu pode muito bem estar a fazê-lo por a conta lhe ter fugido.
     */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken reset = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_TOKEN_INVALID,
                        "Link de recuperação inválido. Pede um novo."));

        if (reset.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.USER_TOKEN_INVALID,
                    "Este link já foi utilizado. Pede um novo se precisares.");
        }

        if (reset.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.USER_TOKEN_EXPIRED,
                    "Este link expirou. Pede um novo.");
        }

        supabaseAuthService.updateUserPassword(reset.getAuthUserId(), newPassword);

        reset.setUsedAt(Instant.now());
        tokenRepository.save(reset);

        Profile profile = profileRepository.findByAuthUserId(reset.getAuthUserId()).orElse(null);
        if (profile != null) {
            profile.setLastTokenResetAt(Instant.now());
            profileRepository.save(profile);
        }

        log.info("Password recuperada para {}", reset.getEmail());
    }
}
