package com.management.managementapi.dto.profile;

import java.util.UUID;

public class ProfileUpdateRequest {
    private UUID profileId;
    private String name;
    private String phoneNumber;


    public UUID getProfileId() {
        return profileId;
    }
    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

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
    // Getters and setters
}