package com.management.managementapi.service.email;

import com.management.managementapi.dto.email.request.EmailProviderUpsertDTO;
import com.management.managementapi.dto.email.response.EmailProviderResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.mapper.email.EmailProviderMapper;
import com.management.managementapi.model.email.EmailProvider;
import com.management.managementapi.repository.email.EmailProviderRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gestão dos provedores SMTP em {@code settings.email_providers}.
 *
 * Até aqui esta tabela só era lida ({@code findDefaultProvider}) e preenchida por
 * {@code INSERT} à mão — sem uma linha lá, o convite de funcionário falhava e não
 * havia forma de o resolver sem acesso à base de dados.
 *
 * Duas regras que a tabela sozinha não garante:
 *
 * <ul>
 *   <li><b>Um só predefinido.</b> Marcar um desmarca os outros. O índice único
 *       parcial da V21 é a rede; a ordem das operações aqui é o que evita
 *       esbarrar nele.</li>
 *   <li><b>O primeiro provedor nasce predefinido.</b> Caso contrário criava-se a
 *       configuração e os emails continuavam a não sair, sem nada a dizer porquê.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EmailProviderService {

    private final EmailProviderRepository repository;
    private final EmailProviderMapper mapper;
    private final EmailService emailService;

    // ── leitura ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EmailProviderResponseDTO> list() {
        return repository.findAllOrdered().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmailProvider getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.EMAIL_PROVIDER_NOT_FOUND,
                        "Provedor de email com ID " + id + " não encontrado"));
    }

    public EmailProviderResponseDTO toResponse(EmailProvider provider) {
        return mapper.toResponse(provider);
    }

    // ── escrita ───────────────────────────────────────────────

    public EmailProvider create(EmailProviderUpsertDTO dto) {
        EmailProvider provider = new EmailProvider();
        apply(dto, provider);

        // Sem password não há autenticação SMTP possível — na criação é obrigatória,
        // ao contrário da edição, onde vir vazia significa "mantém a que está". Por isso
        // é validada aqui e não com um `@NotBlank` no DTO, que é partilhado pelas duas.
        if (dto.password() == null || dto.password().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "A password é obrigatória ao criar um provedor de email");
        }
        provider.setPassword(dto.password());

        boolean first = repository.count() == 0;
        boolean shouldBeDefault = first || Boolean.TRUE.equals(dto.isDefault());
        provider.setIsDefault(false);

        provider = repository.save(provider);
        return shouldBeDefault ? makeDefault(provider) : provider;
    }

    public EmailProvider update(UUID id, EmailProviderUpsertDTO dto) {
        EmailProvider provider = getById(id);
        apply(dto, provider);

        if (dto.password() != null && !dto.password().isBlank()) {
            provider.setPassword(dto.password());
        }

        boolean shouldBeDefault = Boolean.TRUE.equals(dto.isDefault());
        if (shouldBeDefault && !Boolean.TRUE.equals(provider.getIsDefault())) {
            provider = repository.save(provider);
            return makeDefault(provider);
        }
        return repository.save(provider);
    }

    public EmailProvider setDefault(UUID id) {
        return makeDefault(getById(id));
    }

    public EmailProvider setActive(UUID id, boolean active) {
        EmailProvider provider = getById(id);
        provider.setIsActive(active);
        return repository.save(provider);
    }

    public void delete(UUID id) {
        repository.delete(getById(id));
    }

    /**
     * Envia um email de teste com as credenciais gravadas deste provedor.
     *
     * Fora da transação de escrita não faz diferença — não grava nada — mas é
     * deliberadamente o provedor pedido e não o predefinido: serve para validar uma
     * configuração antes de a promover.
     */
    @Transactional(readOnly = true)
    public void sendTest(UUID id, String to) {
        emailService.sendTestEmail(getById(id), to);
    }

    // ── interno ───────────────────────────────────────────────

    private EmailProvider makeDefault(EmailProvider provider) {
        // Primeiro desmarcar os outros: o índice único parcial é verificado a cada
        // statement, por isso marcar antes de limpar dava conflito.
        repository.clearDefaultExcept(provider.getId());

        EmailProvider fresh = getById(provider.getId());
        fresh.setIsDefault(true);
        return repository.save(fresh);
    }

    private void apply(EmailProviderUpsertDTO dto, EmailProvider provider) {
        provider.setProviderName(dto.providerName().trim());
        provider.setHost(dto.host().trim());
        provider.setPort(dto.port());
        provider.setUsername(dto.username().trim());
        provider.setFromEmail(dto.fromEmail().trim());
        provider.setFromName(dto.fromName() == null || dto.fromName().isBlank() ? null : dto.fromName().trim());
        provider.setEncryption(dto.encryption() == null || dto.encryption().isBlank() ? "tls" : dto.encryption());
        provider.setIsActive(dto.isActive() == null || dto.isActive());
    }
}
