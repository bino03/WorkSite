package com.management.managementapi.security;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** Cache simples por request para o profileId do auditor atual. */
@Component
@RequestScope
public class RequestAuditorCache {
  private UUID profileId;

  public UUID getProfileId() { return profileId; }
  public void setProfileId(UUID profileId) { this.profileId = profileId; }
}