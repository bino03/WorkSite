package com.management.managementapi.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "location", schema = "worksite")
public class Location {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  // Relacionamento 1:N com PropertyAsset


  // Campos da Location
  private String addressLine1;
  private String addressLine2;
  private String postalCode;
  private String city;
     @Column(name = "parish")
  private String parish; // Adicionando o campo 'parish' que está na tabela SQL
  private String country;
   @Column(name = "municipality")
    private String municipality;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String googlePlaceId;
  private String notes;

  // Campos de controle de data
  @Column(name = "created_at", nullable = false , updatable = false)
  private Timestamp createdAt;

  @Column(name = "updated_at" ,nullable = false)
  private Timestamp updatedAt;

  // Nome (campo adicional na tabela)
  private String name;

  // Getters e Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }



  public String getAddressLine1() {
    return addressLine1;
  }

  public void setAddressLine1(String addressLine1) {
    this.addressLine1 = addressLine1;
  }

   public String getMunicipality() {
    return municipality;
  }

  public void setMunicipality(String municipality) {
    this.municipality = municipality;
  }

  public String getAddressLine2() {
    return addressLine2;
  }

  public void setAddressLine2(String addressLine2) {
    this.addressLine2 = addressLine2;
  }

  public String getPostalCode() {
    return postalCode;
  }

  public void setPostalCode(String postalCode) {
    this.postalCode = postalCode;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getParish() {
    return parish;
  }

  public void setParish(String parish) {
    this.parish = parish;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public void setLatitude(BigDecimal latitude) {
    this.latitude = latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public void setLongitude(BigDecimal longitude) {
    this.longitude = longitude;
  }

  public String getGooglePlaceId() {
    return googlePlaceId;
  }

  public void setGooglePlaceId(String googlePlaceId) {
    this.googlePlaceId = googlePlaceId;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Timestamp getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Timestamp createdAt) {
    this.createdAt = createdAt;
  }

  public Timestamp getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Timestamp updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

   @PrePersist
  protected void onCreate() {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    if (this.createdAt == null) this.createdAt = now;
    if (this.updatedAt == null) this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = new Timestamp(System.currentTimeMillis());
  }
}
