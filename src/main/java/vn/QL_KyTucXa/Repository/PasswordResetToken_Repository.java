package vn.QL_KyTucXa.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.Entity.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetToken_Repository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByToken(String token);
    List<PasswordResetToken> findByUser_UserId(String userId);
    void deleteByUser_UserId(String userId);

    @Modifying
    @Transactional
    int deleteByExpiresAtBeforeOrIsUsedTrue(LocalDateTime now);
}