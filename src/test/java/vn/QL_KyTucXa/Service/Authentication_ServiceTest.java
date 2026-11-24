package vn.QL_KyTucXa.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.QL_KyTucXa.Config.TelegramProperties;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;
import vn.QL_KyTucXa.DTO.Request.Authentication_Request;
import vn.QL_KyTucXa.DTO.Request.LogoutToken_Request;
import vn.QL_KyTucXa.DTO.Request.RefreshToken_Request;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Entity.LogoutToken;
import vn.QL_KyTucXa.Entity.RefreshToken;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.LogoutToken_Repository;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Authentication_ServiceTest {

    @Mock
    private User_Repository userRepository;
    @Mock
    private LogoutToken_Repository logoutTokenRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private Role_Repository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TelegramProperties telegramProperties;

    @Mock
    private Jwt_Service tokenService;
    @Mock
    private LoginVerificationService twoFactorAuthService;
    @Mock
    private TelegramService notificationService;
    @Mock
    private TokenResponseFactory tokenResponseFactory;
    @Mock
    private HttpRequestClientInfoResolver clientInfoResolver;
    @Mock
    private RoleBasedTwoFactorPolicy twoFactorPolicy;
    @Mock
    private AccountStatusPolicy accountStatusPolicy;
    @Mock
    private User_Service userService;
    @Mock
    private AuditLog_Service auditLogService;

    @InjectMocks
    private Authentication_Service authenticationService;

    private User studentUser;
    private User adminUser;
    private Role studentRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        studentRole = Role.builder()
                .roleId("role-stu")
                .roleCode("STUDENT")
                .roleName("Sinh viên")
                .build();

        adminRole = Role.builder()
                .roleId("role-adm")
                .roleCode("ADMIN")
                .roleName("Quản trị viên")
                .build();

        studentUser = User.builder()
                .userId("u-stu-01")
                .username("sv01")
                .passwordHash("hashed_pwd")
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .role(studentRole)
                .build();

        adminUser = User.builder()
                .userId("u-adm-01")
                .username("admin")
                .passwordHash("hashed_admin")
                .status("ACTIVE")
                .failedLoginAttempts(0)
                .role(adminRole)
                .build();
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("Login thường: cấp token, lưu đúng một RefreshToken qua RefreshTokenService và không có 2FA")
    void login_normalStudent_success_savesRefreshToken() {
        Authentication_Request request = Authentication_Request.builder()
                .username("sv01")
                .password("plain_pwd")
                .build();

        Date accessExp = new Date(System.currentTimeMillis() + 900_000);
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(passwordEncoder.matches("plain_pwd", "hashed_pwd")).thenReturn(true);
        when(twoFactorPolicy.requiresTwoFactorAuth(studentUser)).thenReturn(false);
        when(clientInfoResolver.getClientIpAddress()).thenReturn("192.168.1.50");
        when(clientInfoResolver.getClientUserAgent()).thenReturn("Mozilla/5.0");
        when(tokenResponseFactory.issueSession(eq(studentUser), eq("192.168.1.50"), eq("Mozilla/5.0"), anyString()))
                .thenReturn(Token_Response.builder()
                        .token("access_token_xyz")
                        .refreshToken("refresh_token_abc")
                        .authenticated(true)
                        .requires2FA(false)
                        .status("APPROVED")
                        .expiryTime(accessExp)
                        .username("sv01")
                        .role("STUDENT")
                        .roleName("Sinh viên")
                        .fullName("Nguyễn Văn A")
                        .userCode("SV001")
                        .message("Đăng nhập thành công")
                        .build());

        Token_Response response = authenticationService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access_token_xyz");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token_abc");
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.isRequires2FA()).isFalse();
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getExpiryTime()).isEqualTo(accessExp);
        assertThat(response.getUsername()).isEqualTo("sv01");
        assertThat(response.getMessage()).isEqualTo("Đăng nhập thành công");

        assertThat(studentUser.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(studentUser.getLastLoginAt()).isNotNull();
        verify(userRepository).save(studentUser);

        verify(accountStatusPolicy).assertAccountUsable(studentUser);
        verify(tokenResponseFactory).issueSession(studentUser, "192.168.1.50", "Mozilla/5.0", "Đăng nhập thành công");
        verifyNoInteractions(twoFactorAuthService);
    }

    @Test
    @DisplayName("Login Admin: kích hoạt 2FA Telegram, chưa cấp token")
    void login_admin_triggers2FA() {
        Authentication_Request request = Authentication_Request.builder()
                .username("admin")
                .password("admin_pass")
                .build();

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId("verify-123")
                .username("admin")
                .status("PENDING")
                .build();

        adminUser.setTelegramChatId("123456789");
        lenient().when(telegramProperties.isConfigured()).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("admin_pass", "hashed_admin")).thenReturn(true);
        when(twoFactorPolicy.requiresTwoFactorAuth(adminUser)).thenReturn(true);
        when(clientInfoResolver.getClientIpAddress()).thenReturn("127.0.0.1");
        when(clientInfoResolver.getClientUserAgent()).thenReturn("Web Browser");
        when(twoFactorAuthService.createSession(eq(adminUser), any(), any(), eq("123456789"))).thenReturn(session);
        when(notificationService.sendLoginVerificationMessage(session, "123456789")).thenReturn(999);

        Token_Response response = authenticationService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.isRequires2FA()).isTrue();
        assertThat(response.isAuthenticated()).isFalse();
        assertThat(response.getVerificationId()).isEqualTo("verify-123");
        verify(notificationService).sendLoginVerificationMessage(session, "123456789");
        verify(twoFactorAuthService).updateMessageId("verify-123", 999);
        verify(tokenService, never()).generateToken(any());
        verify(refreshTokenService, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Login thất bại khi tài khoản không tồn tại")
    void login_userNotFound_throwsException() {
        Authentication_Request request = Authentication_Request.builder()
                .username("unknown")
                .password("pass")
                .build();

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authenticationService.login(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("Login thất bại khi tài khoản INACTIVE hoặc BANNED")
    void login_inactiveOrBanned_throwsException() {
        Authentication_Request request = Authentication_Request.builder()
                .username("sv01")
                .password("pass")
                .build();

        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        doThrow(new AppException(ErrorCode.OPERATION_FAILED, "Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt"))
                .when(accountStatusPolicy).assertAccountUsable(studentUser);

        AppException ex = assertThrows(AppException.class, () -> authenticationService.login(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Login sai mật khẩu: tăng số lần sai và ném INVALID_CREDENTIALS")
    void login_wrongPassword_incrementsFailedAttempts() {
        Authentication_Request request = Authentication_Request.builder()
                .username("sv01")
                .password("wrong_pwd")
                .build();

        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(passwordEncoder.matches("wrong_pwd", "hashed_pwd")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authenticationService.login(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(studentUser.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(studentUser);
    }

    @Test
    @DisplayName("Login tài khoản LOCKED nhưng failedLoginAttempts = null không bị NPE")
    void login_lockedUser_nullFailedLoginAttempts_noNpe() {
        Authentication_Request request = Authentication_Request.builder()
                .username("sv01")
                .password("plain_pwd")
                .build();

        studentUser.setStatus("LOCKED");
        studentUser.setFailedLoginAttempts(null);
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        doThrow(new AppException(ErrorCode.OPERATION_FAILED, "Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt"))
                .when(accountStatusPolicy).assertAccountUsable(studentUser);

        AppException ex = assertThrows(AppException.class, () -> authenticationService.login(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
    }

    @Test
    @DisplayName("Login tài khoản LOCKED do nhập sai >= 5 lần, chưa đủ 12h: ném ACCOUNT_LOCKED")
    void login_lockedUser_within12Hours_throwsAccountLocked() {
        Authentication_Request request = Authentication_Request.builder()
                .username("sv01")
                .password("plain_pwd")
                .build();

        studentUser.setStatus("LOCKED");
        studentUser.setFailedLoginAttempts(5);
        studentUser.setUpdatedAt(LocalDateTime.now().minusHours(2));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));

        AppException ex = assertThrows(AppException.class, () -> authenticationService.login(request));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("Login tài khoản LOCKED do nhập sai >= 5 lần, đã qua 12h: tự mở khóa và đăng nhập thành công")
    void login_lockedUser_after12Hours_unlocksAndSucceeds() {
        Authentication_Request request = Authentication_Request.builder()
                .username("sv01")
                .password("plain_pwd")
                .build();

        studentUser.setStatus("LOCKED");
        studentUser.setFailedLoginAttempts(5);
        studentUser.setUpdatedAt(LocalDateTime.now().minusHours(13));
        Date accessExp = new Date(System.currentTimeMillis() + 900_000);

        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(passwordEncoder.matches("plain_pwd", "hashed_pwd")).thenReturn(true);
        when(twoFactorPolicy.requiresTwoFactorAuth(studentUser)).thenReturn(false);
        when(clientInfoResolver.getClientIpAddress()).thenReturn("192.168.1.50");
        when(clientInfoResolver.getClientUserAgent()).thenReturn("Mozilla/5.0");
        when(tokenResponseFactory.issueSession(eq(studentUser), anyString(), anyString(), anyString()))
                .thenReturn(Token_Response.builder()
                        .token("access_token_xyz")
                        .authenticated(true)
                        .status("APPROVED")
                        .build());

        Token_Response response = authenticationService.login(request);

        assertThat(response).isNotNull();
        assertThat(studentUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(studentUser.getFailedLoginAttempts()).isEqualTo(0);
        verify(userRepository, atLeastOnce()).save(studentUser);
    }

    // -------------------------------------------------------- refreshToken

    private SignedJWT signedJwtWithSubject(String subject) {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject(subject).build();
        return new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
    }

    private RefreshToken storedToken(String token, boolean revoked, LocalDateTime revokedAt) {
        return RefreshToken.builder()
                .tokenId("rt-id-1")
                .user(studentUser)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isRevoked(revoked)
                .revokedAt(revokedAt)
                .build();
    }

    @Test
    @DisplayName("Refresh hợp lệ: token cũ bị thu hồi, token mới được lưu, sessionStart giữ nguyên")
    void refreshToken_success_rotates() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();
        RefreshToken stored = storedToken("old_rt", false, null);
        Date newAccessExp = new Date(System.currentTimeMillis() + 900_000);

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("old_rt")).thenReturn(Optional.of(stored));
        when(tokenService.generateToken(studentUser)).thenReturn("new_access");
        when(tokenService.rotateRefreshToken(studentUser, "old_rt")).thenReturn("new_rt");
        when(tokenService.extractExpiration("new_access")).thenReturn(newAccessExp);
        when(clientInfoResolver.getClientIpAddress()).thenReturn("10.0.0.2");
        when(clientInfoResolver.getClientUserAgent()).thenReturn("JUnit-Agent");
        when(tokenResponseFactory.buildTokenResponse(eq(studentUser), eq("new_access"), eq("new_rt"), eq(newAccessExp), anyString()))
                .thenReturn(Token_Response.builder()
                        .token("new_access")
                        .refreshToken("new_rt")
                        .authenticated(true)
                        .requires2FA(false)
                        .status("APPROVED")
                        .expiryTime(newAccessExp)
                        .username("sv01")
                        .message("Làm mới phiên đăng nhập thành công")
                        .build());

        Token_Response result = authenticationService.refreshToken(request);

        assertThat(result.getToken()).isEqualTo("new_access");
        assertThat(result.getRefreshToken()).isEqualTo("new_rt");
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.isRequires2FA()).isFalse();
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getExpiryTime()).isEqualTo(newAccessExp);
        assertThat(result.getUsername()).isEqualTo("sv01");
        assertThat(result.getMessage()).isEqualTo("Làm mới phiên đăng nhập thành công");

        assertThat(stored.getIsRevoked()).isTrue();
        assertThat(stored.getRevokedAt()).isNotNull();
        verify(refreshTokenService).save(stored);

        verify(refreshTokenService).save(studentUser, "new_rt", "10.0.0.2", "JUnit-Agent");
        verify(tokenService).rotateRefreshToken(studentUser, "old_rt");
        verify(tokenService, never()).generateRefreshToken(any(User.class));
        verifyNoInteractions(logoutTokenRepository);
    }

    @Test
    @DisplayName("Refresh bằng token đã thu hồi quá 10 giây: gọi revokeAllActiveByUserId, ném UNAUTHENTICATED")
    void refreshToken_reusedToken_pastGracePeriod_revokesAllSessions() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();
        RefreshToken stored = storedToken("old_rt", true, LocalDateTime.now().minusSeconds(15)); // Quá 10s

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("old_rt")).thenReturn(Optional.of(stored));
        when(refreshTokenService.revokeAllActiveByUserId("u-stu-01")).thenReturn(2);

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(refreshTokenService).revokeAllActiveByUserId("u-stu-01");
        verify(tokenService, never()).generateToken(any());
        verify(refreshTokenService, never()).save(any(User.class), any(), any(), any());
    }

    @Test
    @DisplayName("Refresh bằng token đã thu hồi trong 10 giây (grace period): không gọi revokeAllActiveByUserId, ném TOKEN_INVALID")
    void refreshToken_reusedToken_withinGracePeriod_doesNotRevokeAllSessions() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();
        RefreshToken stored = storedToken("old_rt", true, LocalDateTime.now().minusSeconds(3)); // Trong vòng 3s

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("old_rt")).thenReturn(Optional.of(stored));

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(ex.getMessage()).contains("Yêu cầu làm mới đang được xử lý, vui lòng thử lại");
        verify(refreshTokenService, never()).revokeAllActiveByUserId(any());
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Refresh bằng access token (không phải REFRESH): bị từ chối")
    void refreshToken_accessToken_rejected() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("access_token").build();
        when(tokenService.verifyToken("access_token", true))
                .thenThrow(new AppException(ErrorCode.UNAUTHENTICATED, "Loại token không hợp lệ"));

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(refreshTokenService, logoutTokenRepository, userRepository);
    }

    @Test
    @DisplayName("Refresh khi quá 8 giờ kể từ sessionStart: bị từ chối")
    void refreshToken_sessionTooOld_rejected() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();
        when(tokenService.verifyToken("old_rt", true))
                .thenThrow(new AppException(ErrorCode.UNAUTHENTICATED, "Phiên làm việc đã quá thời hạn tối đa"));

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(tokenService, never()).generateToken(any());
        verify(tokenService, never()).rotateRefreshToken(any(), any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("Refresh khi tài khoản bị khóa: bị từ chối")
    void refreshToken_bannedUser_rejected() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        doThrow(new AppException(ErrorCode.OPERATION_FAILED, "Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt"))
                .when(accountStatusPolicy).assertAccountUsable(studentUser);

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verifyNoInteractions(refreshTokenService);
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Refresh khi không tìm thấy user: USER_NOT_EXISTED")
    void refreshToken_userNotFound() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("ghost"));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXISTED);
    }

    @Test
    @DisplayName("Refresh bằng token hợp lệ về chữ ký nhưng không có trong DB: TOKEN_INVALID")
    void refreshToken_unknownInDb_throwsTokenInvalid() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("old_rt")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("Logout với refresh token rồi sau đó dùng lại token đó (quá 10s): trả TOKEN_INVALID, không thu hồi phiên khác")
    void refreshToken_afterLogout_tokenNotFoundInDb_throwsTokenInvalid_doesNotRevokeAllSessions() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("logged_out_rt").build();

        when(tokenService.verifyToken("logged_out_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("logged_out_rt")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(ex.getMessage()).contains("không tồn tại trong hệ thống");
        verify(refreshTokenService, never()).revokeAllActiveByUserId(any());
        verify(refreshTokenService, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh khi rotateRefreshToken ném AppException: không ghi DB, token cũ không bị thu hồi")
    void refreshToken_rotateThrowsAppException_doesNotRevokeOldTokenOrWriteDb() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();
        RefreshToken stored = storedToken("old_rt", false, null);

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("old_rt")).thenReturn(Optional.of(stored));
        when(tokenService.generateToken(studentUser)).thenReturn("new_access");
        when(tokenService.rotateRefreshToken(studentUser, "old_rt"))
                .thenThrow(new AppException(ErrorCode.TOKEN_INVALID, "Refresh token không hợp lệ"));

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(stored.getIsRevoked()).isFalse();
        assertThat(stored.getRevokedAt()).isNull();
        verify(refreshTokenService, never()).save(any(RefreshToken.class));
        verify(refreshTokenService, never()).save(any(User.class), any(), any(), any());
    }

    @Test
    @DisplayName("Refresh khi resolveUserProfile ném AppException: không ghi DB, token cũ không bị thu hồi")
    void refreshToken_profileResolutionThrowsAppException_doesNotRevokeOldTokenOrWriteDb() throws Exception {
        RefreshToken_Request request = RefreshToken_Request.builder().token("old_rt").build();
        RefreshToken stored = storedToken("old_rt", false, null);
        Date accessExp = new Date(System.currentTimeMillis() + 900_000);

        when(tokenService.verifyToken("old_rt", true)).thenReturn(signedJwtWithSubject("sv01"));
        when(userRepository.findByUsername("sv01")).thenReturn(Optional.of(studentUser));
        when(refreshTokenService.findByToken("old_rt")).thenReturn(Optional.of(stored));
        when(tokenService.generateToken(studentUser)).thenReturn("new_access");
        when(tokenService.rotateRefreshToken(studentUser, "old_rt")).thenReturn("new_rt");
        when(tokenService.extractExpiration("new_access")).thenReturn(accessExp);
        when(tokenResponseFactory.buildTokenResponse(eq(studentUser), eq("new_access"), eq("new_rt"), eq(accessExp), anyString()))
                .thenThrow(new AppException(ErrorCode.OPERATION_FAILED, "Lỗi lấy thông tin hồ sơ"));

        AppException ex = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(stored.getIsRevoked()).isFalse();
        assertThat(stored.getRevokedAt()).isNull();
        verify(refreshTokenService, never()).save(any(RefreshToken.class));
        verify(refreshTokenService, never()).save(any(User.class), any(), any(), any());
    }

    // -------------------------------------------------------------- logout

    @Test
    @DisplayName("Logout với refreshToken: refresh token bị xóa qua deleteIfExists")
    void logout_withRefreshToken_deletesIt() {
        LogoutToken_Request request = LogoutToken_Request.builder().refreshToken("rt_to_delete").build();

        authenticationService.logout(request);

        verify(refreshTokenService).deleteIfExists("rt_to_delete");
        verifyNoInteractions(logoutTokenRepository);
    }

    @Test
    @DisplayName("Logout với access token đã hết hạn: vẫn thành công, refresh token vẫn bị xóa")
    void logout_expiredAccessToken_stillDeletesRefreshToken() throws Exception {
        when(tokenService.verifyToken("expired_access", false))
                .thenThrow(new AppException(ErrorCode.UNAUTHENTICATED, "Token không hợp lệ hoặc đã hết hạn"));

        LogoutToken_Request request = LogoutToken_Request.builder()
                .token("expired_access")
                .refreshToken("rt_to_delete")
                .build();

        assertDoesNotThrow(() -> authenticationService.logout(request));

        verify(refreshTokenService).deleteIfExists("rt_to_delete");
        verify(logoutTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Logout với access token hợp lệ: lưu LogoutToken theo jti, user lấy từ claim, thông tin thiết bị lấy từ server")
    void logout_validAccessToken_blacklistsJti() throws Exception {
        Date exp = new Date(System.currentTimeMillis() + 600_000);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS512),
                new JWTClaimsSet.Builder()
                        .jwtID("jwt-id-123")
                        .expirationTime(exp)
                        .claim("userId", "u-stu-01")
                        .build());
        when(tokenService.verifyToken("active_token", false)).thenReturn(signedJWT);
        when(userRepository.findById("u-stu-01")).thenReturn(Optional.of(studentUser));
        when(clientInfoResolver.getClientIpAddress()).thenReturn("10.1.1.1");
        when(clientInfoResolver.getClientUserAgent()).thenReturn("Server-Agent");
        when(logoutTokenRepository.existsByToken("jwt-id-123")).thenReturn(false);

        LogoutToken_Request request = LogoutToken_Request.builder()
                .token("active_token")
                .deviceInfo("client-spoofed")
                .ipAddress("6.6.6.6")
                .build();

        authenticationService.logout(request);

        ArgumentCaptor<LogoutToken> captor = ArgumentCaptor.forClass(LogoutToken.class);
        verify(logoutTokenRepository).save(captor.capture());
        LogoutToken saved = captor.getValue();
        assertThat(saved.getTokenId()).isEqualTo("jwt-id-123");
        assertThat(saved.getToken()).isEqualTo("jwt-id-123");
        assertThat(saved.getUser()).isSameAs(studentUser);
        assertThat(saved.getExpiresAt()).isEqualTo(Jwt_Service.toLocalDateTime(exp));
        assertThat(saved.getDeviceInfo()).isEqualTo("Server-Agent");
        assertThat(saved.getIpAddress()).isEqualTo("10.1.1.1");
        assertThat(saved.getLogoutAt()).isNotNull();
    }

    @Test
    @DisplayName("Logout hai lần với cùng access token: không lỗi, không tạo bản ghi trùng")
    void logout_twice_noDuplicate_noError() throws Exception {
        Date exp = new Date(System.currentTimeMillis() + 600_000);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS512),
                new JWTClaimsSet.Builder()
                        .jwtID("jwt-id-123")
                        .expirationTime(exp)
                        .claim("userId", "u-stu-01")
                        .build());
        when(tokenService.verifyToken("active_token", false)).thenReturn(signedJWT);
        when(logoutTokenRepository.existsByToken("jwt-id-123")).thenReturn(true); // Đã tồn tại trong blacklist!

        LogoutToken_Request request = LogoutToken_Request.builder().token("active_token").build();

        assertDoesNotThrow(() -> authenticationService.logout(request));
        verify(logoutTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkLoginStatus gọi hai lần sau khi duyệt: lần đầu trả token, lần sau không trả token")
    void checkLoginStatus_calledTwice_firstReturnsToken_secondThrowsExpired() {
        Token_Response tokenResponse = Token_Response.builder()
                .token("access_123")
                .refreshToken("refresh_123")
                .authenticated(true)
                .build();

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId("verify-abc")
                .status("APPROVED")
                .tokenResponse(tokenResponse)
                .build();

        when(twoFactorAuthService.getSession("verify-abc")).thenReturn(session);

        // Lần 1: Nhận token thành công
        Token_Response firstCall = authenticationService.checkLoginStatus("verify-abc");
        assertThat(firstCall).isNotNull();
        assertThat(firstCall.getToken()).isEqualTo("access_123");
        assertThat(session.getTokenResponse()).isNull(); // Đã bị xóa khỏi session

        // Lần 2: Ném ngoại lệ vì token đã được tiêu thụ
        AppException ex = assertThrows(AppException.class,
                () -> authenticationService.checkLoginStatus("verify-abc"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_2FA_EXPIRED);
        assertThat(ex.getMessage()).contains("đã được sử dụng");
    }
}
