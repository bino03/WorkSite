package com.management.managementapi.util;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra simétrica para segredos guardados em repouso — hoje só a password SMTP
 * de {@code settings.email_providers}, via {@link com.management.managementapi.model.converters.EncryptedStringConverter}.
 *
 * <p>AES-256-GCM, IV de 12 bytes aleatório por valor, tag de 128 bits. O valor
 * cifrado é {@code gcm:<base64 iv>:<base64 ct+tag>}. Um valor sem o prefixo
 * {@code gcm:} é tratado como texto em claro legado — {@link #decrypt} devolve-o
 * tal e qual, o que torna a introdução da cifra num não-evento (o runner de
 * arranque re-cifra-os).
 *
 * <p><b>Chaves</b> (base64 de 16/24/32 bytes):
 * <ul>
 *   <li>{@code app.crypto.email-key} — a ativa. Obrigatória: sem ela a app não
 *       arranca. Cifra e decifra.</li>
 *   <li>{@code app.crypto.email-key-previous} — opcional, só decifra. É a rede
 *       durante uma rotação: pôr a chave antiga aqui, a nova na ativa, reiniciar
 *       (o runner re-cifra tudo com a ativa) e remover esta a seguir.</li>
 * </ul>
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String PREFIX = "gcm:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec activeKey;
    private final SecretKeySpec previousKey;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(
            @Value("${app.crypto.email-key:}") String activeKeyB64,
            @Value("${app.crypto.email-key-previous:}") String previousKeyB64) {
        this.activeKey = parseKey(activeKeyB64, "app.crypto.email-key (APP_EMAIL_CRYPTO_KEY)", true);
        this.previousKey = parseKey(previousKeyB64,
                "app.crypto.email-key-previous (APP_EMAIL_CRYPTO_KEY_PREVIOUS)", false);
    }

    @PostConstruct
    void announce() {
        log.info("SecretCipher pronto — chave ativa {} bytes, chave anterior {}.",
                activeKey.getEncoded().length,
                previousKey != null ? previousKey.getEncoded().length + " bytes" : "ausente");
    }

    /** {@code null} → {@code null}. Caso contrário devolve {@code gcm:iv:ct}. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder b64 = Base64.getEncoder();
            return PREFIX + b64.encodeToString(iv) + ":" + b64.encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Falha a cifrar um segredo", e);
        }
    }

    /**
     * {@code null} → {@code null}. Um valor sem o prefixo {@code gcm:} é devolvido
     * tal e qual (texto em claro legado). Um valor {@code gcm:} é decifrado com a
     * chave ativa e, se falhar e existir chave anterior, com essa.
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("falta o iv ou o texto");
            }
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] iv = b64.decode(parts[0]);
            byte[] ct = b64.decode(parts[1]);
            try {
                return decryptWith(activeKey, iv, ct);
            } catch (Exception withActive) {
                if (previousKey != null) {
                    return decryptWith(previousKey, iv, ct);
                }
                throw withActive;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Falha a decifrar um segredo — a chave não corresponde ao valor guardado, "
                            + "ou o valor está corrompido", e);
        }
    }

    /** {@code true} se o valor está no formato {@code gcm:...} (i.e. já foi cifrado). */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * O valor tal como deve ficar guardado com a chave <b>ativa</b>. Devolve o
     * mesmo objeto quando já está cifrado com a chave ativa (nada a fazer); um
     * valor novo quando estava em claro ou só a chave anterior o decifra. É o que
     * o runner de arranque usa para completar uma rotação sem re-escrever o que
     * já está bem.
     */
    public String reEncryptForActiveKey(String stored) {
        if (stored == null) {
            return null;
        }
        if (isEncrypted(stored)) {
            String[] parts = stored.substring(PREFIX.length()).split(":", 2);
            if (parts.length == 2) {
                try {
                    Base64.Decoder b64 = Base64.getDecoder();
                    decryptWith(activeKey, b64.decode(parts[0]), b64.decode(parts[1]));
                    return stored; // já está com a chave ativa
                } catch (Exception notActive) {
                    // em claro legado ou chave anterior — cai para re-cifrar
                }
            }
        }
        return encrypt(decrypt(stored));
    }

    private String decryptWith(SecretKeySpec key, byte[] iv, byte[] ct) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    private static SecretKeySpec parseKey(String b64, String name, boolean required) {
        if (b64 == null || b64.isBlank()) {
            if (required) {
                throw new IllegalStateException(name + " em falta — é obrigatória. "
                        + "Gerar com `openssl rand -base64 32` e pôr no .env.");
            }
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " não é base64 válido", e);
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(name + " tem " + raw.length
                    + " bytes — o AES precisa de 16, 24 ou 32 (recomendado 32).");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
