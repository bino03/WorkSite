package com.management.managementapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.management.managementapi.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    /**
     * O email não vive em `worksite.profile` — está em `auth.users`, que esta
     * entidade mapeia. É por aqui que a recuperação de password resolve o email
     * escrito no formulário para um utilizador do Supabase.
     *
     * Sem distinção de maiúsculas: o Supabase normaliza o email ao criar a conta,
     * mas quem o escreve num formulário de recuperação não normaliza nada.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    @Override
    @NonNull
    Optional<User> findById(@NonNull UUID id);

    @Override
    @NonNull
    <S extends User> S save(@NonNull S entity);
}
