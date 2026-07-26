package com.management.managementapi.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class TokenHash {
  private TokenHash() {}

  public static String sha256B64(String token) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      byte[] dig = md.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(dig);
    } catch (Exception e) {
      throw new RuntimeException("Failed to hash token", e);
    }
  }
}
