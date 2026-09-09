package com.management.managementapi.infra;

import com.management.managementapi.util.SecretCipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * No arranque, garante que toda a {@code settings.email_providers.password} está
 * cifrada com a chave <b>ativa</b> ({@code app.crypto.email-key}).
 *
 * <p>Cobre dois casos: os valores que existiam em claro antes de a cifra entrar,
 * e — depois de uma rotação — os que só a chave anterior decifrava. Lê os valores
 * em cru por {@link JdbcTemplate} (sem passar pelo {@code EncryptedStringConverter})
 * e só re-escreve os que mudam, por isso é barato e idempotente.
 */
@Component
@Order(20) // depois do DataSourceDiagnostics (que valida a ligação)
public class EmailProviderPasswordReEncryptRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailProviderPasswordReEncryptRunner.class);

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    public EmailProviderPasswordReEncryptRunner(JdbcTemplate jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows =
                jdbc.queryForList("select id, password from settings.email_providers");

        int rewritten = 0;
        for (Map<String, Object> row : rows) {
            String stored = (String) row.get("password");
            String desired = cipher.reEncryptForActiveKey(stored);
            if (desired != null && !desired.equals(stored)) {
                jdbc.update(
                        "update settings.email_providers set password = ? where id = ?",
                        desired, (UUID) row.get("id"));
                rewritten++;
            }
        }

        if (rewritten > 0) {
            log.info("SMTP: {} password(s) de provedor de email re-cifradas com a chave ativa.", rewritten);
        } else if (!rows.isEmpty()) {
            log.info("SMTP: as {} password(s) de provedor de email já estão cifradas com a chave ativa.",
                    rows.size());
        }
    }
}
