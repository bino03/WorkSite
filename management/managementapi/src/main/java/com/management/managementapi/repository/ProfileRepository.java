package com.management.managementapi.repository;

import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;

import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByAuthUserId(UUID authUserId);

    boolean existsByAuthUserId(UUID authUserId);

    Optional<Profile> findByName(String name);

    @Query("SELECT p, u.email FROM Profile p JOIN User u ON u.id = p.authUserId WHERE u.id = :authUserId")
    Optional<Tuple> findProfileWithEmailByAuthUserId(@Param("authUserId") UUID authUserId);

    @Override
    @NonNull
    Optional<Profile> findById(@NonNull UUID id);

    @Override
    @NonNull
    <S extends Profile> S save(@NonNull S entity);

    Long countByRole(ProfileRole role);

    @Query("select p.name from Profile p where p.id = :id")
    Optional<String> findNameOnlyById(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Profile p
           set p.name = :name,
               p.phoneNumber = :phone
         where p.authUserId = :authUserId
    """)
    int updateNamePhone(@Param("authUserId") UUID authUserId,
                        @Param("name") String name,
                        @Param("phone") String phone);

    List<Profile> findAllByAccountStatus(AccountStatus accountStatus);

    @Query("SELECT u.email FROM Profile p JOIN User u ON u.id = p.authUserId WHERE p.role = :role AND u.email IS NOT NULL")
    List<String> findEmailsByRole(@Param("role") ProfileRole role);

}
