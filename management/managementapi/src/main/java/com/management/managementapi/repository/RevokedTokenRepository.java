package com.management.managementapi.repository;

import com.management.managementapi.model.RevokedToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

  boolean existsByTokenHash(String tokenHash); // usa o índice único

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(value = """
      delete from worksite.revoked_token
      where (expires_at is not null and expires_at < now())
         or (expires_at is null and revoked_at < now() - (?1 || ' seconds')::interval)
      """, nativeQuery = true)
  int deleteExpired(int ttlSeconds);
}
