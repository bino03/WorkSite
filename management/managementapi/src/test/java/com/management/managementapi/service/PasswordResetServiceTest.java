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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Duas propriedades que este fluxo tem de manter, e que são fáceis de partir sem dar
 * por isso:
 *
 * <ol>
 *   <li><b>Não revela contas.</b> Pedir recuperação para um email que não existe sai
 *       exactamente como para um que existe — sem exceção, sem 404. Caso contrário o
 *       endpoint, que é público, passa a servir para descobrir quem tem conta.</li>
 *   <li><b>Um token serve uma vez.</b> Usado ou fora do prazo, não define password
 *       nenhuma.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private SupabaseAuthService supabaseAuthService;
    @Mock private EmailService emailService;

    private static final String EMAIL = "ana@empresa.pt";
    private static final UUID AUTH_USER_ID = UUID.randomUUID();

    private PasswordResetService service() {
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(c -> c.getArgument(0));
        return new PasswordResetService(
                tokenRepository, userRepository, profileRepository, supabaseAuthService, emailService);
    }

    private void contaExisteComPerfil() {
        User user = new User();
        user.setId(AUTH_USER_ID);
        user.setEmail(EMAIL);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.of(new Profile()));
    }

    private PasswordResetToken token(Instant expiresAt, Instant usedAt) {
        PasswordResetToken reset = new PasswordResetToken();
        reset.setId(UUID.randomUUID());
        reset.setAuthUserId(AUTH_USER_ID);
        reset.setEmail(EMAIL);
        reset.setToken("token-abc");
        reset.setExpiresAt(expiresAt);
        reset.setUsedAt(usedAt);
        return reset;
    }

    @Test
    @DisplayName("email sem conta não rebenta nem envia nada — a resposta é igual à do email com conta")
    void emailDesconhecidoNaoRevelaNada() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service().requestReset("nao-existe@empresa.pt")).doesNotThrowAnyException();

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("conta de auth sem perfil não recebe link")
    void contaSemPerfilNaoRecebeLink() {
        User user = new User();
        user.setId(AUTH_USER_ID);
        user.setEmail(EMAIL);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> service().requestReset(EMAIL)).doesNotThrowAnyException();

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("pedido válido queima os anteriores e envia o token que acabou de gravar")
    void pedidoValidoEnviaToken() {
        contaExisteComPerfil();

        service().requestReset("  " + EMAIL.toUpperCase() + "  ");

        verify(tokenRepository).invalidatePending(any(UUID.class), any(Instant.class));

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getValue().getUsedAt()).isNull();

        // O email vai para o endereço tal como está gravado no Supabase, não para o
        // que foi escrito no formulário.
        verify(emailService).sendPasswordResetEmail(EMAIL, saved.getValue().getToken());
    }

    @Test
    @DisplayName("token já usado não define password nenhuma")
    void tokenJaUsado() {
        when(tokenRepository.findByToken("token-abc"))
                .thenReturn(Optional.of(token(Instant.now().plusSeconds(600), Instant.now())));

        assertThatThrownBy(() -> service().reset("token-abc", "password-nova"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_TOKEN_INVALID);

        verify(supabaseAuthService, never()).updateUserPassword(any(), anyString());
    }

    @Test
    @DisplayName("token fora do prazo não define password nenhuma")
    void tokenExpirado() {
        when(tokenRepository.findByToken("token-abc"))
                .thenReturn(Optional.of(token(Instant.now().minusSeconds(60), null)));

        assertThatThrownBy(() -> service().reset("token-abc", "password-nova"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_TOKEN_EXPIRED);

        verify(supabaseAuthService, never()).updateUserPassword(any(), anyString());
    }

    @Test
    @DisplayName("token válido define a password, queima o link e derruba as sessões abertas")
    void tokenValidoDefinePassword() {
        PasswordResetToken valido = token(Instant.now().plusSeconds(600), null);
        Profile profile = new Profile();
        when(tokenRepository.findByToken("token-abc")).thenReturn(Optional.of(valido));
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.of(profile));

        service().reset("token-abc", "password-nova");

        verify(supabaseAuthService).updateUserPassword(AUTH_USER_ID, "password-nova");
        assertThat(valido.getUsedAt()).isNotNull();
        // O AccountLockFilter recusa JWTs emitidos antes deste instante.
        assertThat(profile.getLastTokenResetAt()).isNotNull();
    }
}
