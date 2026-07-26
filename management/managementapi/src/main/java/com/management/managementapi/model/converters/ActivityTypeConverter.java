package com.management.managementapi.model.converters;

import com.management.managementapi.model.enums.ActivityType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ActivityTypeConverter implements AttributeConverter<ActivityType, String> {

    @Override
    public String convertToDatabaseColumn(ActivityType attribute) {
        if (attribute == null) return null;
        return attribute.getDbValue();
    }

    @Override
    public ActivityType convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return ActivityType.fromString(dbData);
    }
}
