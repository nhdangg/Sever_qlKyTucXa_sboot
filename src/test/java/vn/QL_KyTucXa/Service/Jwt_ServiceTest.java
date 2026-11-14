package vn.QL_KyTucXa.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.CheckToken_Request;
import vn.QL_KyTucXa.DTO.CheckToken_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.LogoutToken_Repository;
import vn.QL_KyTucXa.Repository.RolePermission_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Jwt_ServiceTest {

    @Mock
    private LogoutToken_Repository logoutTokenRepository;

    @Mock
    private RolePermission_Repository rolePermissionRepository;

    @Mock
    private User_Repository userRepository;

    @InjectMocks
    private Jwt_Service jwtService;

    private User user;
    private final String SECRET_KEY = "d41d8cd98f00b204e9800998ecf8427e068b5a034d610811e2f754388e6e5a4f1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    @BeforeEach
    void setUp() {
        jwtService.signerKey = SECRET_KEY;
        jwtService.validDuration = 900;           // 15 phút
        jwtService.refreshableDuration = 3600;     // 1 giờ
        jwtService.maxSessionDuration = 28800;     // 8 giờ

        Role role = Role.builder()
                .roleId("role-01")
                .roleCode("STUDENT")
                .roleName("Sinh viên")
                .build();

        user = User.builder()
                .userId("u-01")
                .username("student_test")
                .status("ACTIVE")
                .role(role)
                .build();
    }

    @Test
    @DisplayName("Kiểm tra signerKey: ném lỗi nếu khóa ký ngắn hơn 64 bytes")
    void validateSignerKey_throwsExceptionWhenTooShort() {
        jwtService.signerKey = "short_key_less_than_64_bytes_1234567890";
        assertThrows(IllegalStateException.class, () -> jwtService.validateSignerKey());
    }

    @Test
    @DisplayName("Kiểm tra signerKey: thành công khi khóa ký đủ từ 64 bytes trở lên")
    void validateSignerKey_successWhenKeySufficientLength() {
        jwtService.signerKey = SECRET_KEY;
        jwtService.validateSignerKey();
    }

    @Test
    @DisplayName("Sinh JWT token và trích xuất đúng thông tin username, userId, scope")
    void generateToken_and_extractClaims() {
        when(rolePermissionRepository.findPermissionCodesByRoleId("role-01"))
                .thenReturn(List.of("VIEW_PROFILE", "EDIT_PROFILE"));

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("student_test");
        assertThat(jwtService.extractUserId(token)).isEqualTo("u-01");
        assertThat(jwtService.extractExpiration(token)).isAfter(new Date());
        assertThat(jwtService.extractJwtId(token)).isNotBlank();
        assertThat(jwtService.extractScope(token)).contains("ROLE_STUDENT", "VIEW_PROFILE", "EDIT_PROFILE");
    }

    @Test
    @DisplayName("Sinh Refresh Token: claim tokenType = REFRESH và sessionStart được ghi nhận")
    void generateRefreshToken_claims() throws Exception {
        Instant sessionStart = Instant.now().minus(10, ChronoUnit.MINUTES);
        String refreshToken = jwtService.generateRefreshToken(user, sessionStart);

        SignedJWT parsed = SignedJWT.parse(refreshToken);
        var claims = parsed.getJWTClaimsSet();

        assertThat(claims.getClaim("tokenType")).isEqualTo("REFRESH");
        assertThat(claims.getClaim("userId")).isEqualTo("u-01");
        assertThat(((Number) claims.getClaim("sessionStart")).longValue())
                .isEqualTo(sessionStart.getEpochSecond());

        long duration = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;
        assertThat(duration).isEqualTo(3600);
    }

    @Test
    @DisplayName("Xoay vòng Refresh Token: giữ nguyên sessionStart của token cũ")
    void rotateRefreshToken_preservesSessionStart() throws Exception {
        Instant originalStart = Instant.now().minus(2, ChronoUnit.HOURS);
        String oldToken = jwtService.generateRefreshToken(user, originalStart);

        String rotatedToken = jwtService.rotateRefreshToken(user, oldToken);

        SignedJWT parsed = SignedJWT.parse(rotatedToken);
        var claims = parsed.getJWTClaimsSet();

        assertThat(((Number) claims.getClaim("sessionStart")).longValue())
                .isEqualTo(originalStart.getEpochSecond());
    }

    @Test
    @DisplayName("Xoay vòng Refresh Token: ném lỗi nếu token cũ không có claim sessionStart")
    void rotateRefreshToken_missingSessionStart_throws() throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("QL_KyTucXa")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .jwtID(UUID.randomUUID().toString())
                .claim("tokenType", "REFRESH")
                .build();

        JWSObject jwsObject = new JWSObject(header, new Payload(claimsSet.toJSONObject()));
        jwsObject.sign(new MACSigner(SECRET_KEY.getBytes()));
        String tokenWithoutSessionStart = jwsObject.serialize();

        assertThrows(AppException.class,
                () -> jwtService.rotateRefreshToken(user, tokenWithoutSessionStart));
    }

    @Test
    @DisplayName("verifyToken: access token hợp lệ được xác thực thành công")
    void verifyToken_validAccessToken_success() throws Exception {
        String token = jwtService.generateToken(user);
        when(logoutTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByUsername("student_test")).thenReturn(Optional.of(user));

        SignedJWT signedJWT = jwtService.verifyToken(token, false);
        assertThat(signedJWT).isNotNull();
    }

    @Test
    @DisplayName("verifyToken: ném lỗi nếu token đã đăng xuất (jti có trong blacklist)")
    void verifyToken_loggedOutToken_throws() {
        String token = jwtService.generateToken(user);
        when(logoutTokenRepository.existsByToken(anyString())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> jwtService.verifyToken(token, false));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ex.getMessage()).contains("đã bị vô hiệu hóa");
    }

    @Test
    @DisplayName("verifyToken: dùng Access Token làm Refresh Token bị chặn")
    void verifyToken_accessTokenUsedAsRefreshToken_throws() {
        String accessToken = jwtService.generateToken(user);

        AppException ex = assertThrows(AppException.class, () -> jwtService.verifyToken(accessToken, true));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ex.getMessage()).contains("yêu cầu Refresh Token");
    }

    @Test
    @DisplayName("verifyToken: dùng Refresh Token làm Access Token bị chặn")
    void verifyToken_refreshTokenUsedAsAccessToken_throws() {
        String refreshToken = jwtService.generateRefreshToken(user);

        AppException ex = assertThrows(AppException.class, () -> jwtService.verifyToken(refreshToken, false));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ex.getMessage()).contains("không được dùng Refresh Token làm Access Token");
    }

    @Test
    @DisplayName("verifyToken: refresh token vượt quá giới hạn 8 giờ bị chặn")
    void verifyToken_refreshToken_exceedsEightHours_throws() {
        Instant eightHoursOneMinAgo = Instant.now().minus(8, ChronoUnit.HOURS).minus(1, ChronoUnit.MINUTES);
        String oldSessionToken = jwtService.generateRefreshToken(user, eightHoursOneMinAgo);

        AppException ex = assertThrows(AppException.class, () -> jwtService.verifyToken(oldSessionToken, true));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ex.getMessage()).contains("Phiên làm việc đã quá thời hạn tối đa");
    }

    @Test
    @DisplayName("verifyToken: refresh token thiếu sessionStart bị chặn")
    void verifyToken_refreshToken_missingSessionStart_throws() throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("QL_KyTucXa")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .jwtID(UUID.randomUUID().toString())
                .claim("tokenType", "REFRESH")
                .build();

        JWSObject jwsObject = new JWSObject(header, new Payload(claimsSet.toJSONObject()));
        jwsObject.sign(new MACSigner(SECRET_KEY.getBytes()));
        String tokenWithoutSessionStart = jwsObject.serialize();

        assertThrows(AppException.class,
                () -> jwtService.verifyToken(tokenWithoutSessionStart, true));
    }

    @Test
    @DisplayName("verifyToken: tài khoản bị khóa ném UNAUTHENTICATED ngay cả khi token còn hạn")
    void verifyToken_lockedAccount_throws() {
        String token = jwtService.generateToken(user);
        when(logoutTokenRepository.existsByToken(anyString())).thenReturn(false);

        User lockedUser = User.builder().username("student_test").status("LOCKED").build();
        when(userRepository.findByUsername("student_test")).thenReturn(Optional.of(lockedUser));

        AppException ex = assertThrows(AppException.class, () -> jwtService.verifyToken(token, false));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ex.getMessage()).contains("Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt");
    }

    @Test
    @DisplayName("checkToken với refresh token trả về checkToken = false")
    void checkToken_withRefreshToken_returnsFalse() throws Exception {
        String refreshToken = jwtService.generateRefreshToken(user);

        CheckToken_Response response = jwtService.checkToken(
                CheckToken_Request.builder().Token(refreshToken).build());

        assertThat(response.getCheckToken()).isFalse();
    }

    @Test
    @DisplayName("checkToken với access token hợp lệ trả về checkToken = true")
    void checkToken_withValidAccessToken_returnsTrue() throws Exception {
        String accessToken = jwtService.generateToken(user);
        when(logoutTokenRepository.existsByToken(anyString())).thenReturn(false);
        when(userRepository.findByUsername("student_test")).thenReturn(Optional.of(user));

        CheckToken_Response response = jwtService.checkToken(
                CheckToken_Request.builder().Token(accessToken).build());

        assertThat(response.getCheckToken()).isTrue();
    }

    @Test
    @DisplayName("checkToken với tài khoản bị khóa trả về checkToken = false")
    void checkToken_withLockedAccount_returnsFalse() throws Exception {
        String accessToken = jwtService.generateToken(user);
        when(logoutTokenRepository.existsByToken(anyString())).thenReturn(false);

        User lockedUser = User.builder().username("student_test").status("LOCKED").build();
        when(userRepository.findByUsername("student_test")).thenReturn(Optional.of(lockedUser));

        CheckToken_Response response = jwtService.checkToken(
                CheckToken_Request.builder().Token(accessToken).build());

        assertThat(response.getCheckToken()).isFalse();
    }

    @Test
    @DisplayName("Helper toLocalDateTime chuyển đổi chính xác theo ZoneId.systemDefault()")
    void toLocalDateTime_convertsCorrectly() {
        Date now = new Date();
        LocalDateTime ldt = Jwt_Service.toLocalDateTime(now);

        assertThat(ldt).isEqualTo(now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        assertThat(Jwt_Service.toLocalDateTime(null)).isNull();
    }

    @Test
    @DisplayName("Helper limit cắt chuỗi đúng giới hạn")
    void limit_cutsProperly() {
        assertThat(Jwt_Service.limit(null, 10)).isNull();
        assertThat(Jwt_Service.limit("short", 10)).isEqualTo("short");
        assertThat(Jwt_Service.limit("1234567890EXTRA", 10)).isEqualTo("1234567890");
    }
}

