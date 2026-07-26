package com.management.managementapi.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;

/**
 * Perfil interno (admins/funcionários). Ligações de auditoria em created_by/updated_by.
 */
@Entity
@Table(name = "profile", schema = "pm")
public class Profile extends BaseEntity {

    @Column(name = "id", unique = true)
    private UUID id; // ponte opcional para auth.users

    @Column(name = "auth_user_id", unique = true)
    private UUID authUserId; // ponte opcional para auth.users

    @Column(nullable = false)
    private String name;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "last_token_reset_at")
    private Instant lastTokenResetAt;

    @Column(name = "photo_bucket")
    private String photoBucket = "private";  // Padrão de bucket (pode ser alterado para 'documents' se necessário)

    @Column(name = "photo_key")
    private String photoKey;

    // enum DB; String aqui
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "account_status", columnDefinition = "pm.account_status_enum", nullable = false)
    private AccountStatus accountStatus = AccountStatus.unlocked;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", columnDefinition = "pm.role_enum", nullable = false)
    private ProfileRole role = ProfileRole.EMPLOYEE;

    // =================== Getters/Setters ===================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAuthUserId() { return authUserId; }
    public void setAuthUserId(UUID authUserId) { this.authUserId = authUserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public ProfileRole getRole() { return role; }
    public void setRole(ProfileRole role) { this.role = role; }

    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }

    public Instant getLastTokenResetAt() {
        return lastTokenResetAt;
    }

    public void setLastTokenResetAt(Instant lastTokenResetAt) {
        this.lastTokenResetAt = lastTokenResetAt;
    }

    public String getPhotoBucket() {
        return photoBucket;
    }

    public void setPhotoBucket(String photoBucket) {
        this.photoBucket = photoBucket;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }
}
