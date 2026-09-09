package com.management.managementapi.model.email;



import com.management.managementapi.model.converters.EncryptedStringConverter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_providers", schema = "settings")
@Getter
@Setter
public class EmailProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false, length = 255)
    private String username;

    /**
     * Guardada cifrada (AES-256-GCM) pelo {@link EncryptedStringConverter}. Em
     * memória é sempre o valor em claro; a coluna é {@code text} desde a V34
     * (o {@code gcm:iv:ct} não cabe nos 255 originais). Nunca sai da API — o
     * mapper só expõe {@code hasPassword}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String password;

    @Column(name = "from_email", nullable = false, length = 255)
    private String fromEmail;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(length = 20)
    private String encryption = "tls";

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}