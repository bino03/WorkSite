package com.management.managementapi.model.converters;

import com.management.managementapi.util.SecretCipher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedStringConverterTest {

    private static String key() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final EncryptedStringConverter converter =
            new EncryptedStringConverter(new SecretCipher(key(), ""));

    @Test
    @DisplayName("Escreve cifrado, lê em claro")
    void roundTrip() {
        String db = converter.convertToDatabaseColumn("smtp-secret");

        assertThat(db).startsWith("gcm:");
        assertThat(db).doesNotContain("smtp-secret");
        assertThat(converter.convertToEntityAttribute(db)).isEqualTo("smtp-secret");
    }

    @Test
    @DisplayName("null passa nos dois sentidos; um valor legado em claro lê-se tal e qual")
    void nullsAndLegacy() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("valor-antigo-em-claro"))
                .isEqualTo("valor-antigo-em-claro");
    }
}
