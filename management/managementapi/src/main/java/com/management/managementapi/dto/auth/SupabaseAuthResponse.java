package com.management.managementapi.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SupabaseAuthResponse {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("expires_at")
    private Long expiresAt;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("user")
    private SupabaseUser user;

    @Data
    public static class SupabaseUser {
        private String id;
        private String email;
        private String role;

        @JsonProperty("user_metadata")
        private UserMetadata userMetadata;

        @Data
        public static class UserMetadata {
            private String role;
            private String name;
        }
    }
}
