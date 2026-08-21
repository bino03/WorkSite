package com.management.managementapi.dto.auth;

import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class AuthDTO {

  public static class LoginRequest {
    @Email @NotBlank public String email;
    @NotBlank public String password;
  }

  public static class LoginResponse {
    public String accessToken;
    public String refreshToken;
    public Long expiresIn;
    public String tokenType;
    public UUID authUserId;
    public UUID profileId;
    public String name;
  }

  public static class AdminCreateRequest {
    @Email @NotBlank public String email;
    @NotBlank public String password;
    public String name;
    public String phone;
    @NotNull public ProfileRole role;
  }

  public static class AdminLinkToRequest {
    @NotNull public UUID authUserId;
    public String name;
    public String phone;
    @NotNull public ProfileRole role;
  }

  public static class AccountStatusRequest {
    @NotNull public UUID profileId;
    @NotNull public AccountStatus status; // unlocked|blocked|deleted
  }

  public record SendInviteRequest(
    @NotBlank @Email String email,
    String phone,
    @NotNull ProfileRole role
) {}

public record AcceptInviteRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String name
) {}

/** Pedido de link de recuperação. A resposta é sempre igual, exista ou não a conta. */
public record ForgotPasswordRequest(
    @NotBlank @Email String email
) {}

/** O `min = 8` acompanha o do convite — as duas formas definem a mesma password. */
public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8) String password
) {}


public record InviteListItemResponse(
    UUID id,
    String email,
    String phone,
    ProfileRole role,
    String status,
    Instant sentAt,
    Instant acceptedAt,
    Instant expiresAt,
    UUID invitedBy,
    String invitedByName  // Nome de quem enviou
) {}
}