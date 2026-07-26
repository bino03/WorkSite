package com.management.managementapi.enterprises.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.management.managementapi.model.enums.Visibility;

import java.io.IOException;

public class VisibilityDeserializer extends JsonDeserializer<Visibility> {
    
    @Override
    public Visibility deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null) {
            return null;
        }
        
        // Aceita tanto "public" como "PUBLIC"
        if (value.equalsIgnoreCase("public")) {
            return Visibility.PUBLIC;
        } else if (value.equalsIgnoreCase("private")) {
            return Visibility.PRIVATE;
        }
        
        throw new IllegalArgumentException("Unknown visibility value: " + value);
    }
}