package com.management.managementapi.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = true)
    private String email;


        private String encryptedPassword;  // Campo para armazenar a senha criptografada


    // Adiciona outros campos necessários da tabela `auth.users`
        public String getEncryptedPassword() { return encryptedPassword; }

 public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }
    // Getters e Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Outros campos conforme necessário
}