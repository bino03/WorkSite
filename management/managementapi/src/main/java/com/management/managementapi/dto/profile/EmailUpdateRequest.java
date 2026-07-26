package com.management.managementapi.dto.profile;

import java.util.UUID;

public class EmailUpdateRequest {
    private UUID userId;
    private String email;

    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    // Getters and setters  



}