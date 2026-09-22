package com.management.managementapi.service.employee;

import com.management.managementapi.service.ProfileService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bloquear/eliminar uma conta tem de cortar sessões já abertas, não só impedir logins novos.
 * O mecanismo é escrever `last_token_reset_at = now()` — o `AccountLockFilter` já o lê da BD a
 * cada pedido (o mesmo usado no reset de password). Antes desta correção (2026-09-22), nada
 * fazia isto: uma conta "bloqueada" continuava com acesso total até o access token expirar
 * sozinho, sem nenhuma sincronização de volta para o Supabase Auth.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeAccountLockTest {

    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private AuditorAware<UUID> auditorAware;
    @Mock private ProfileService profileService;

    private static final UUID ALVO = UUID.randomUUID();

    private EmployeeServiceImpl service() {
        return new EmployeeServiceImpl(jdbc, auditorAware, profileService);
    }

    @Test
    @DisplayName("blockProfile escreve last_token_reset_at para cortar sessões já abertas")
    void blockWritesLastTokenResetAt() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        // blockProfile chama getById() a seguir ao update, por um caminho do jdbc que este
        // teste não stuba (query, não update) — não interessa ao que se testa aqui, só o update.
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        try {
            service().blockProfile(ALVO);
        } catch (Exception ignoreGetByIdFailure) {
            // getById() sem stub de query pode falhar depois do update já ter corrido.
        }

        verify(jdbc).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getValue()).containsIgnoringCase("last_token_reset_at");
        assertThat(sql.getValue()).contains("'blocked'");
    }

    @Test
    @DisplayName("deleteProfile escreve last_token_reset_at para cortar sessões já abertas")
    void deleteWritesLastTokenResetAt() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        service().deleteProfile(ALVO);

        verify(jdbc).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getValue()).containsIgnoringCase("last_token_reset_at");
        assertThat(sql.getValue()).contains("'deleted'");
    }
}
