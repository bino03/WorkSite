package com.management.managementapi.model.enums;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.management.managementapi.enterprises.model.VisibilityDeserializer;

@JsonDeserialize(using = VisibilityDeserializer.class) // ← ADICIONA ESTA LINHA

public enum Visibility {
  PRIVATE("private"),
  PUBLIC("public");

  private final String dbValue;

  Visibility(String dbValue) {
    this.dbValue = dbValue;
  }

  public String getDbValue() {
    return dbValue;
  }

  @Override
  public String toString() {
    return dbValue;
  }


  
   public static Visibility fromString(String value) {
    for (Visibility visibility : values()) {
      if (visibility.dbValue.equalsIgnoreCase(value)) {
        return visibility;
      }
    }
    throw new IllegalArgumentException("Unknown visibility: " + value);
  }
}