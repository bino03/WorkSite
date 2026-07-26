package com.management.managementapi.dto.admin;

import com.management.managementapi.model.enums.ProfileRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendInviteRequest {

    @NotBlank
    @Email
    private String email;

    private String phone;

    @NotNull
    private ProfileRole role;
}
