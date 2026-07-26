package com.management.managementapi.model.converters;

import com.management.managementapi.model.enums.EntityType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EntityTypeConverter implements AttributeConverter<EntityType, String> {

    @Override
    public String convertToDatabaseColumn(EntityType attribute) {
        if (attribute == null) return null;
        return attribute.getDbValue();
    }

    @Override
    public EntityType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return EntityType.fromString(dbData);
    }
}
