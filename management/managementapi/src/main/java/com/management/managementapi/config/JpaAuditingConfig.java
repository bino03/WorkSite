// src/main/java/com/management/managementapi/config/JpaAuditingConfig.java
package com.management.managementapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing  // usa o AuditorAware<UUID>
public class JpaAuditingConfig { }
