package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.Repository.LogoutToken_Repository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LogoutToken_Repository logoutTokenRepository;

    @InjectMocks
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Test
    @DisplayName("cleanupExpiredTokens dọn dẹp cả refresh token và logout token hết hạn")
    void cleanupExpiredTokens_invokesPurgeOnAllRepositories() {
        when(refreshTokenService.deleteExpiredTokens()).thenReturn(10);
        when(logoutTokenRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(5);

        tokenCleanupScheduler.cleanupExpiredTokens();

        verify(refreshTokenService).deleteExpiredTokens();
        verify(logoutTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
