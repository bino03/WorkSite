package com.management.managementapi.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY_A = randomKey();
    private static final String KEY_B = randomKey();

    private static String randomKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SecretCipher cipher(String active, String previous) {
        return new SecretCipher(active, previous);
    }

    @Test
    @DisplayName("Cifrar e decifrar devolve o original; o formato é gcm:iv:ct")
    void roundTrip() {
        SecretCipher c = cipher(KEY_A, "");

        String enc = c.encrypt("smtp-p@ss-123");

        assertThat(enc).startsWith("gcm:");
        assertThat(enc.split(":")).hasSize(3);
        assertThat(c.decrypt(enc)).isEqualTo("smtp-p@ss-123");
        assertThat(c.isEncrypted(enc)).isTrue();
    }

    @Test
    @DisplayName("Cada cifra do mesmo valor é diferente (IV aleatório)")
    void nonDeterministic() {
        SecretCipher c = cipher(KEY_A, "");
        assertThat(c.encrypt("x")).isNotEqualTo(c.encrypt("x"));
    }

    @Test
    @DisplayName("null entra, null sai; texto sem prefixo é devolvido tal e qual (legado)")
    void nullsAndLegacyPlaintext() {
        SecretCipher c = cipher(KEY_A, "");
        assertThat(c.encrypt(null)).isNull();
        assertThat(c.decrypt(null)).isNull();
        assertThat(c.decrypt("password-em-claro")).isEqualTo("password-em-claro");
        assertThat(c.isEncrypted("password-em-claro")).isFalse();
    }

    @Test
    @DisplayName("A chave anterior decifra o que foi cifrado com ela")
    void previousKeyDecrypts() {
        String encWithA = cipher(KEY_A, "").encrypt("segredo");

        SecretCipher rotated = cipher(KEY_B, KEY_A); // ativa = B, anterior = A
        assertThat(rotated.decrypt(encWithA)).isEqualTo("segredo");
    }

    @Test
    @DisplayName("Um valor adulterado não decifra")
    void tamperedFails() {
        SecretCipher c = cipher(KEY_A, "");
        String enc = c.encrypt("segredo");
        String tampered = enc.substring(0, enc.length() - 2) + (enc.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> c.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Sem chave ativa, ou com chave de tamanho errado, não arranca")
    void badKeysRejected() {
        assertThatThrownBy(() -> cipher("", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("obrigatória");

        assertThatThrownBy(() -> cipher(Base64.getEncoder().encodeToString(new byte[10]), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bytes");

        assertThatThrownBy(() -> cipher("nao-e-base64-!!!", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reEncryptForActiveKey: claro → cifrado; já ativo → intacto; chave anterior → recifrado")
    void reEncrypt() {
        SecretCipher withA = cipher(KEY_A, "");
        SecretCipher rotated = cipher(KEY_B, KEY_A);

        // texto em claro legado → passa a gcm: e decifra para o mesmo
        String fromPlain = rotated.reEncryptForActiveKey("claro");
        assertThat(rotated.isEncrypted(fromPlain)).isTrue();
        assertThat(rotated.decrypt(fromPlain)).isEqualTo("claro");

        // já cifrado com a chave ativa → devolve o mesmo, sem re-escrever
        String encWithB = rotated.encrypt("ok");
        assertThat(rotated.reEncryptForActiveKey(encWithB)).isSameAs(encWithB);

        // cifrado só com a chave anterior → é re-cifrado, e decifra para o mesmo
        String encWithA = withA.encrypt("valor");
        String moved = rotated.reEncryptForActiveKey(encWithA);
        assertThat(moved).isNotEqualTo(encWithA);
        assertThat(rotated.decrypt(moved)).isEqualTo("valor");

        assertThat(rotated.reEncryptForActiveKey(null)).isNull();
    }
}
