package com.management.managementapi.dto.common.location;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocationUpsertDTO {

  private UUID id;

  @Size(max = 200)
  private String addressLine1;

  @Size(max = 200)
  private String addressLine2;

  @Size(max = 16)
  private String postalCode;

  @Size(max = 120)
  private String city;

  @Size(max = 120)
  private String state;

  @Size(max = 120)
  private String country;

  @Size(max = 120)
  private String municipality;

  private String parish;

  // latitude [-90, 90], longitude [-180, 180]
  @DecimalMin(value = "-90.0")
  @DecimalMax(value = "90.0")
  private BigDecimal latitude;

  @DecimalMin(value = "-180.0")
  @DecimalMax(value = "180.0")
  private BigDecimal longitude;

  @Size(max = 120)
  private String googlePlaceId;

  @Size(max = 500)
  private String notes;

  // getters/setters
  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getAddressLine1() { return addressLine1; }
  public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

  public String getMunicipality() { return municipality; }
  public void setMunicipality(String municipality) { this.municipality = municipality; }

  public String getAddressLine2() { return addressLine2; }
  public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

  public String getParish() { return parish; }
  public void setParish(String parish) { this.parish = parish; }

  public String getPostalCode() { return postalCode; }
  public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }

  public String getState() { return state; }
  public void setState(String state) { this.state = state; }

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
