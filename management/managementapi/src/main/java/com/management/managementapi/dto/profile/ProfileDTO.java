package com.management.managementapi.dto.profile;

import java.time.OffsetDateTime;

public class ProfileDTO {

    private String name;
    private String phoneNumber;
    private String role;
    private String accountStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String email;  // Novo campo para o email

    // Getters e Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getEmail() {
        return email;  // Getter para email
    }

    public void setEmail(String email) {
        this.email = email;  // Setter para email
    }
}
