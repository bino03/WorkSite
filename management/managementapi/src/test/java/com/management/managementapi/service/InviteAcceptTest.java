package com.management.managementapi.service;

import com.management.managementapi.dto.auth.SupabaseAuthResponse;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.model.PendingInvite;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.repository.PendingInviteRepository;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `/auth/accept-invite` é público: quem lhe chega não está autenticado e traz
 * apenas um token vindo de um email. Todas as portas de saída que não são "criar
 * a conta" têm de estar fechadas à chave, e o convite não pode servir duas vezes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteAcceptTest {

    @Mock private PendingInviteRepository pendingInviteRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseAuthService supabaseAuthService;

    private static final String TOKEN = "5c3d1a90-0000-4000-8000-000000000001";

    private InviteService service() {
        return new InviteService(pendingInviteRepository, profileRepository, supabaseAuthService);
    }

    private PendingInvite invite(PendingInvite.InviteStatus status, Instant expiresAt) {
        PendingInvite invite = new PendingInvite();
        invite.setId(UUID.randomUUID());
        invite.setEmail("novo@empresa.pt");
        invite.setPhone("910000000");
        invite.setRole(ProfileRole.EMPLOYEE);
        invite.setInviteToken(TOKEN);
        invite.setStatus(status);
        invite.setExpiresAt(expiresAt);
        invite.setSentAt(Instant.now());
        invite.setInvitedBy(UUID.randomUUID());
        return invite;
    }

    private void supabaseCreatesUser() {
        SupabaseAuthResponse.SupabaseUser user = new SupabaseAuthResponse.SupabaseUser();
        user.setId(UUID.randomUUID().toString());
        user.setEmail("novo@empresa.pt");
        when(supabaseAuthService.createUser(anyString(), anyString())).thenReturn(user);
    }

    @Test
    @DisplayName("convite válido cria o perfil com o role e o telefone que vinham do convite")
    void conviteValidoCriaPerfil() {
        PendingInvite pending = invite(PendingInvite.InviteStatus.PENDING, Instant.now().plusSeconds(3600));
        when(pendingInviteRepository.findByInviteToken(TOKEN)).thenReturn(Optional.of(pending));
        supabaseCreatesUser();

        service().accept(TOKEN, "password-forte", "  Ana Silva  ");

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(saved.capture());

        assertThat(saved.getValue().getName()).isEqualTo("Ana Silva");
        assertThat(saved.getValue().getRole()).isEqualTo(ProfileRole.EMPLOYEE);
        assertThat(saved.getValue().getPhoneNumber()).isEqualTo("910000000");
        assertThat(pending.getStatus()).isEqualTo(PendingInvite.InviteStatus.ACCEPTED);
        assertThat(pending.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("token desconhecido não cria conta nenhuma")
    void tokenDesconhecido() {
        when(pendingInviteRepository.findByInviteToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().accept(TOKEN, "password-forte", "Ana"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_TOKEN_INVALID);

        verify(supabaseAuthService, never()).createUser(anyString(), anyString());
    }

    @Test
    @DisplayName("um convite já aceite não serve segunda vez")
    void conviteJaAceite() {
        PendingInvite aceite = invite(PendingInvite.InviteStatus.ACCEPTED, Instant.now().plusSeconds(3600));
        when(pendingInviteRepository.findByInviteToken(TOKEN)).thenReturn(Optional.of(aceite));

        assertThatThrownBy(() -> service().accept(TOKEN, "password-forte", "Ana"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_TOKEN_INVALID);

        verify(supabaseAuthService, never()).createUser(anyString(), anyString());
    }

    @Test
    @DisplayName("convite fora do prazo é recusado e fica marcado como expirado")
    void conviteExpiradoFicaMarcado() {
        PendingInvite caducado = invite(PendingInvite.InviteStatus.PENDING, Instant.now().minusSeconds(60));
        when(pendingInviteRepository.findByInviteToken(TOKEN)).thenReturn(Optional.of(caducado));

        assertThatThrownBy(() -> service().accept(TOKEN, "password-forte", "Ana"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_TOKEN_EXPIRED);

        // Marcar é o que liberta o email para um convite novo: `existsByEmailAndStatus`
        // só bloqueia enquanto houver um PENDING.
        assertThat(caducado.getStatus()).isEqualTo(PendingInvite.InviteStatus.EXPIRED);
        verify(pendingInviteRepository).save(caducado);
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    @DisplayName("convite cancelado não serve")
    void conviteCancelado() {
        PendingInvite cancelado = invite(PendingInvite.InviteStatus.CANCELLED, Instant.now().plusSeconds(3600));
        when(pendingInviteRepository.findByInviteToken(TOKEN)).thenReturn(Optional.of(cancelado));

        assertThatThrownBy(() -> service().accept(TOKEN, "password-forte", "Ana"))
                .isInstanceOf(BusinessException.class);

        verify(supabaseAuthService, never()).createUser(anyString(), anyString());
    }
}
