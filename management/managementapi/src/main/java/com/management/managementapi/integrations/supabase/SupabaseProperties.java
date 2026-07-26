package com.management.managementapi.integrations.supabase;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * application.yml:
 * supabase:
 *   url: https://<project-ref>.supabase.co
 *   service-role-key: <SERVICE_ROLE_KEY>
 *   jwt:
 *     secret: <JWT_SECRET>
 */
@Validated
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {

  @NotBlank
  private String url;

  @NotBlank
  private String serviceRoleKey;
      private String anonKey; // Adicione esta propriedade


  private Jwt jwt = new Jwt();

  // getters/setters
  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }

   public String getAnonKey() { return anonKey; }
  public void setAnonKey(String anonKey) { this.anonKey = anonKey; }

  public String getServiceRoleKey() { return serviceRoleKey; }
  public void setServiceRoleKey(String serviceRoleKey) { this.serviceRoleKey = serviceRoleKey; }

  public Jwt getJwt() { return jwt; }
  public void setJwt(Jwt jwt) { this.jwt = jwt; }

  // sub-props: supabase.jwt.secret
  public static class Jwt {
    @NotBlank
    private String secret;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
  }
}
