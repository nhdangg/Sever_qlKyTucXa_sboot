package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.Repository.LogoutToken_Repository;
import vn.QL_KyTucXa.Repository.PasswordResetToken_Repository;

import java.time.LocalDateTime;

/**
 * Job định kỳ dọn dẹp các Refresh Token, Logout Token (Blacklist) và Password Reset Token đã hết hạn khỏi CSDL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenCleanupScheduler {

    RefreshTokenService refreshTokenService;
    LogoutToken_Repository logoutTokenRepository;
    PasswordResetToken_Repository passwordResetTokenRepository;

    /**
     * Chạy định kỳ mỗi giờ một lần (3600000 ms) để dọn dẹp các token đã quá hạn
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedRefresh = refreshTokenService.deleteExpiredTokens();
        int deletedLogout = logoutTokenRepository.deleteByExpiresAtBefore(now);
        int deletedPasswordReset = passwordResetTokenRepository.deleteByExpiresAtBeforeOrIsUsedTrue(now);

        if (deletedRefresh > 0 || deletedLogout > 0 || deletedPasswordReset > 0) {
            log.info("Dọn dẹp token định kỳ: đã xóa {} refresh token, {} blacklist token và {} password reset token hết hạn/đã dùng",
                    deletedRefresh, deletedLogout, deletedPasswordReset);
        }
    }
}
