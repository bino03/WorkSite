package com.management.managementapi.dto.profile;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public class PasswordUpdateRequest {
    private UUID userId;
    private String password;

    @NotBlank(message = "A palavra-passe atual é obrigatória.")
    private String currentPassword;


    // Getters and setters
    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }


    public String getCurrentPassword() {
        return currentPassword;
    }
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }


    public String getNewPassword() {
        return password;
    }
    public void setNewPassword(String password) {
        this.password = password;
    }
}

