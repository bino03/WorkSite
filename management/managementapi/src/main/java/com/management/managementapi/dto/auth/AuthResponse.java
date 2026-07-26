package com.management.managementapi.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private UserData user;

    @Data
    @AllArgsConstructor
    public static class UserData {
        private String id;
        private String email;
        private String name;
        private String role;
        private String photoUrl;
        private String profileId;
    }
}
