package com.management.managementapi.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.management.managementapi.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    @Override
    @NonNull
    Optional<User> findById(@NonNull UUID id);

    @Override
    @NonNull
    <S extends User> S save(@NonNull S entity);
}
