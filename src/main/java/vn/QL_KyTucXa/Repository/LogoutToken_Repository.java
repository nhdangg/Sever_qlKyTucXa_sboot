package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.QL_KyTucXa.Entity.LogoutToken;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface LogoutToken_Repository extends JpaRepository<LogoutToken, String> {
    Optional<LogoutToken> findByToken(String token);
    boolean existsByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LogoutToken l where l.expiresAt < :dateTime")
    int deleteByExpiresAtBefore(@Param("dateTime") LocalDateTime dateTime);
}