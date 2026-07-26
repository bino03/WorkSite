package com.management.managementapi.dto.profile;

import java.util.UUID;

import com.management.managementapi.model.enums.ProfileRole;

public class ProfileListResponseDTO {
    
    private UUID id;
    private String name;
    private String email;
    private ProfileRole role;
    private String phoneNumber;

    // Construtores
    public ProfileListResponseDTO() {
    }

    public ProfileListResponseDTO(UUID id, String name, String email, ProfileRole role, String phoneNumber) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.phoneNumber = phoneNumber;
    }

    // Getters e Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public ProfileRole getRole() {
        return role;
    }

    public void setRole(ProfileRole role) {
        this.role = role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}