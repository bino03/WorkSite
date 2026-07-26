package com.management.managementapi.dto.common.location;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocationResponseDTO {
  private UUID id;
  private String addressLine1;
  private String addressLine2;
  private String postalCode;
  private String city;
  private String parish;
  private String municipality;

  private String country;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String googlePlaceId;
  private String notes;

  // Getters e Setters
  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getAddressLine1() { return addressLine1; }
  public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

  public String getMunicipality() { return municipality; }
  public void setMunicipality(String municipality) { this.municipality = municipality; }

  public String getAddressLine2() { return addressLine2; }
  public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

  public String getPostalCode() { return postalCode; }
  public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }

  public String getParish() { return parish; }
  public void setParish(String parish) { this.parish = parish; }

  public String getCountry() { return country; }
  public void setCountry(String country) { this.country = country; }

  public BigDecimal getLatitude() { return latitude; }
  public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

  public BigDecimal getLongitude() { return longitude; }
  public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

  public String getGooglePlaceId() { return googlePlaceId; }
  public void setGooglePlaceId(String googlePlaceId) { this.googlePlaceId = googlePlaceId; }

  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
}
