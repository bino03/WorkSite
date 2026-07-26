package com.management.managementapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "supabase")
public class SupabaseConfig {
    private String url;
    private String anonKey;
    private String authUrl;
    private String serviceRoleKey;
    private Jwt jwt = new Jwt();

    @Data
    public static class Jwt {
        private String secret;
    }
}
