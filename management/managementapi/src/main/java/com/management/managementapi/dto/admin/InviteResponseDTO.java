package com.management.managementapi.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteResponseDTO {

    private UUID id;
    private String email;
    private String phone;
    private String role;
    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant sentAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant acceptedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    private String invitedBy;
    private String invitedByName;
}
