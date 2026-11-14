package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.Entity.RefreshToken;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Repository.RefreshToken_Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshToken_Repository refreshTokenRepository;

    @Mock
    private Jwt_Service jwtService;

    @Mock
    private CurrentUser_Service currentUserService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().roleId("role-01").roleCode("STUDENT").build();
        user = User.builder().userId("u-01").username("student01").role(role).build();
    }

    @Test
    @DisplayName("save lưu đúng thông tin, băm SHA-256 độ dài 64 ký tự, cắt chuỗi ip/ua")
    void save_persistsCorrectly() {
        String token = "sample_refresh_token_jwt_very_long_string";
        Date exp = new Date(System.currentTimeMillis() + 3600_000);
        when(jwtService.extractExpiration(token)).thenReturn(exp);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String longUa = "A".repeat(300);
        String longIp = "1".repeat(60);

        RefreshToken saved = refreshTokenService.save(user, token, longIp, longUa);

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getToken()).isEqualTo(RefreshTokenService.hashToken(token));
        assertThat(saved.getToken()).hasSize(64);
        assertThat(saved.getIsRevoked()).isFalse();
        assertThat(saved.getExpiresAt()).isEqualTo(Jwt_Service.toLocalDateTime(exp));
        assertThat(saved.getDeviceInfo()).hasSize(255);
        assertThat(saved.getIpAddress()).hasSize(45);
    }

    @Test
    @DisplayName("deleteIfExists khi token tồn tại thì tìm theo mã băm SHA-256 và xóa khỏi repository")
    void deleteIfExists_tokenExists_deletesFromRepo() {
        String token = "tok";
        String hashedToken = RefreshTokenService.hashToken(token);
        RefreshToken stored = RefreshToken.builder()
                .tokenId("rt-1")
                .token(hashedToken)
                .isRevoked(false)
                .build();
        when(refreshTokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(stored));

        refreshTokenService.deleteIfExists(token);

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    @DisplayName("deleteIfExists khi token không tồn tại thì không làm gì")
    void deleteIfExists_tokenNotFound_doesNotDelete() {
        String token = "not-found";
        String hashedToken = RefreshTokenService.hashToken(token);
        when(refreshTokenRepository.findByToken(hashedToken)).thenReturn(Optional.empty());

        refreshTokenService.deleteIfExists(token);

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("hashToken sinh đúng chuỗi 64 ký tự hex và giữ nguyên nếu đã là SHA-256")
    void hashToken_generatesConsistentSha256() {
        String rawToken = "my_custom_jwt_refresh_token";
        String hash1 = RefreshTokenService.hashToken(rawToken);
        String hash2 = RefreshTokenService.hashToken(rawToken);

        assertThat(hash1).hasSize(64);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(RefreshTokenService.hashToken(hash1)).isEqualTo(hash1);
    }

    @Test
    @DisplayName("revokeAllActiveByUserId gọi đúng repository method")
    void revokeAllActiveByUserId_callsRepo() {
        when(refreshTokenRepository.revokeAllActiveByUserId(eq("u-01"), any(LocalDateTime.class))).thenReturn(3);

        int count = refreshTokenService.revokeAllActiveByUserId("u-01");

        assertThat(count).isEqualTo(3);
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq("u-01"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("deleteExpiredTokens gọi đúng repository method và trả về số lượng bản ghi bị xóa")
    void deleteExpiredTokens_callsRepo() {
        when(refreshTokenRepository.deleteExpiredTokens(any(LocalDateTime.class))).thenReturn(5);

        int count = refreshTokenService.deleteExpiredTokens();

        assertThat(count).isEqualTo(5);
        verify(refreshTokenRepository).deleteExpiredTokens(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("deleteIfExists với userId trùng khớp với chủ sở hữu thì xóa thành công")
    void deleteIfExists_withMatchingUserId_deletesFromRepo() {
        String token = "tok-owned";
        String hashedToken = RefreshTokenService.hashToken(token);
        RefreshToken stored = RefreshToken.builder()
                .tokenId("rt-1")
                .token(hashedToken)
                .user(user)
                .isRevoked(false)
                .build();
        when(refreshTokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(stored));

        refreshTokenService.deleteIfExists(token, "u-01");

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    @DisplayName("deleteIfExists với userId khác chủ sở hữu thì từ chối xóa token của người khác")
    void deleteIfExists_withDifferentUserId_doesNotDelete() {
        String token = "tok-victim";
        String hashedToken = RefreshTokenService.hashToken(token);
        RefreshToken stored = RefreshToken.builder()
                .tokenId("rt-victim")
                .token(hashedToken)
                .user(user)
                .isRevoked(false)
                .build();
        when(refreshTokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(stored));

        refreshTokenService.deleteIfExists(token, "attacker-user-id");

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteIfExists không truyền userId nhưng có SecurityContext của kẻ khác thì từ chối xóa")
    void deleteIfExists_viaCurrentUserSecurityContext_preventsDeletingOtherUserToken() {
        String token = "tok-victim-2";
        String hashedToken = RefreshTokenService.hashToken(token);
        RefreshToken stored = RefreshToken.builder()
                .tokenId("rt-victim-2")
                .token(hashedToken)
                .user(user)
                .isRevoked(false)
                .build();
        when(currentUserService.userId()).thenReturn("attacker-user-id");
        when(refreshTokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(stored));

        refreshTokenService.deleteIfExists(token);

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    @DisplayName("save thực thể RefreshToken tự động băm SHA-256 chuỗi token nếu chưa băm")
    void save_rawEntity_hashesTokenAutomatically() {
        String rawToken = "raw_refresh_token_to_be_hashed";
        RefreshToken rawEntity = RefreshToken.builder()
                .user(user)
                .token(rawToken)
                .build();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken saved = refreshTokenService.save(rawEntity);

        assertThat(saved.getToken()).hasSize(64);
        assertThat(saved.getToken()).isEqualTo(RefreshTokenService.hashToken(rawToken));
        verify(refreshTokenRepository).save(rawEntity);
    }
}
