package com.management.managementapi.model.converters;

import com.management.managementapi.model.enums.Visibility;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VisibilityConverter implements AttributeConverter<Visibility, String> {

    @Override
    public String convertToDatabaseColumn(Visibility attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDbValue(); // Retorna "private" ou "public"
    }

    @Override
    public Visibility convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Visibility.fromString(dbData);
    }
}