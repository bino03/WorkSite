package com.management.managementapi.service.email;

import com.management.managementapi.dto.email.request.EmailProviderUpsertDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.mapper.email.EmailProviderMapper;
import com.management.managementapi.model.email.EmailProvider;
import com.management.managementapi.repository.email.EmailProviderRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * As regras que a tabela sozinha não garante e o Backoffice assume.
 *
 * A que custa mais caro estar errada é a primeira: se o primeiro provedor criado
 * não nascesse predefinido, quem acabasse de configurar o SMTP continuaria a ver
 * "Nenhum provedor de email configurado" sem nada a explicar porquê — que é
 * exactamente o beco que esta funcionalidade veio desfazer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailProviderServiceTest {

    @Mock private EmailProviderRepository repository;
    @Mock private EmailProviderMapper mapper;
    @Mock private EmailService emailService;

    private EmailProviderService service() {
        // save() devolve a entidade que recebe, com id atribuído — é o que a base de
        // dados faz e o que o makeDefault precisa para desmarcar os outros.
        when(repository.save(any(EmailProvider.class))).thenAnswer(call -> {
            EmailProvider saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(repository.findById(any(UUID.class))).thenAnswer(call -> {
            UUID id = call.getArgument(0);
            return Optional.ofNullable(lastSaved != null && id.equals(lastSaved.getId()) ? lastSaved : null);
        });
        return new EmailProviderService(repository, mapper, emailService);
    }

    private EmailProvider lastSaved;

    private EmailProviderUpsertDTO dto(String password, Boolean isDefault) {
        return new EmailProviderUpsertDTO(
                "Gmail", "smtp.gmail.com", 587, "obra@empresa.pt", password,
                "obra@empresa.pt", "Worksite", "tls", isDefault, true);
    }

    @Test
    @DisplayName("o primeiro provedor criado fica predefinido mesmo sem o pedirem")
    void primeiroProvedorFicaPredefinido() {
        when(repository.count()).thenReturn(0L);
        EmailProviderService service = service();
        captureSaves();

        EmailProvider created = service.create(dto("segredo", false));

        assertThat(created.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("com provedores já configurados, criar sem pedir predefinido não mexe no que está")
    void segundoProvedorNaoRoubaOPredefinido() {
        when(repository.count()).thenReturn(1L);
        EmailProviderService service = service();
        captureSaves();

        EmailProvider created = service.create(dto("segredo", false));

        assertThat(created.getIsDefault()).isFalse();
        verify(repository, never()).clearDefaultExcept(any());
    }

    @Test
    @DisplayName("criar sem password é recusado — sem ela não há autenticação SMTP")
    void criarSemPasswordERecusado() {
        when(repository.count()).thenReturn(0L);
        EmailProviderService service = service();

        assertThatThrownBy(() -> service.create(dto("   ", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("editar sem preencher a password mantém a que está gravada")
    void editarSemPasswordMantemAAtual() {
        EmailProvider existente = new EmailProvider();
        existente.setId(UUID.randomUUID());
        existente.setPassword("password-antiga");
        existente.setIsDefault(false);
        lastSaved = existente;

        EmailProviderService service = service();
        captureSaves();

        EmailProvider updated = service.update(existente.getId(), dto(null, false));

        assertThat(updated.getPassword()).isEqualTo("password-antiga");
        assertThat(updated.getProviderName()).isEqualTo("Gmail");
    }

    @Test
    @DisplayName("marcar um como predefinido desmarca os outros antes de o marcar")
    void marcarPredefinidoDesmarcaOsOutros() {
        EmailProvider existente = new EmailProvider();
        existente.setId(UUID.randomUUID());
        existente.setPassword("segredo");
        existente.setIsDefault(false);
        lastSaved = existente;

        EmailProviderService service = service();
        captureSaves();

        EmailProvider promovido = service.setDefault(existente.getId());

        verify(repository).clearDefaultExcept(existente.getId());
        assertThat(promovido.getIsDefault()).isTrue();
    }

    /** Mantém o `findById` a devolver o que foi gravado por último. */
    private void captureSaves() {
        when(repository.save(any(EmailProvider.class))).thenAnswer(call -> {
            EmailProvider saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            lastSaved = saved;
            return saved;
        });
    }
}
