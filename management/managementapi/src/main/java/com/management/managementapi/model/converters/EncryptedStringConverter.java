package com.management.managementapi.model.converters;

import com.management.managementapi.util.SecretCipher;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.stereotype.Component;

/**
 * Cifra em repouso os campos de string marcados com
 * {@code @Convert(converter = EncryptedStringConverter.class)} — hoje só
 * {@code EmailProvider.password}.
 *
 * <p>Não é {@code autoApply}: aplica-se campo a campo, de propósito. A entidade
 * em memória tem sempre o valor em claro; a coluna tem sempre o {@code gcm:...}
 * (ou texto em claro legado, até o runner de arranque o re-cifrar).
 *
 * <p>É um bean Spring para receber o {@link SecretCipher} — o Hibernate resolve
 * conversores pelo {@code SpringBeanContainer} que o Spring Boot regista.
 */
@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final SecretCipher cipher;

    public EncryptedStringConverter(SecretCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
