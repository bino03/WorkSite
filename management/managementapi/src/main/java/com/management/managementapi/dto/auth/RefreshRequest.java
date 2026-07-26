package com.management.managementapi.dto.auth;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken; // Opcional - virá do cookie
}
