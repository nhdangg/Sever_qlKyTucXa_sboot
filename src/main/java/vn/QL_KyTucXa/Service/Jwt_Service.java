package vn.QL_KyTucXa.Service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.QL_KyTucXa.DTO.CheckToken_Request;
import vn.QL_KyTucXa.DTO.CheckToken_Response;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.LogoutToken_Repository;
import vn.QL_KyTucXa.Repository.RolePermission_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class Jwt_Service {

    private final LogoutToken_Repository logoutTokenRepository;
    private final RolePermission_Repository rolePermissionRepository;
    private final User_Repository userRepository;

    @NonFinal
    @Value("${jwt.signer-key}")
    protected String signerKey;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long validDuration; // 15 phút (900 giây)

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long refreshableDuration; // 1 giờ (3600 giây)

    @NonFinal
    @Value("${jwt.max-session-duration:28800}")
    protected long maxSessionDuration; // 8 giờ (28800 giây)

    // Kiểm tra độ dài khóa ký: HS512 yêu cầu tối thiểu 64 bytes (512 bits)
    @PostConstruct
    public void validateSignerKey() {
        if (signerKey == null || signerKey.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException(
                    "JWT signer-key phải có độ dài ít nhất 64 bytes (512 bits) cho thuật toán HS512");
        }
    }

    // Tạo Access Token cho người dùng
    public String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("QL_KyTucXa")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(validDuration, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getUserId())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes(StandardCharsets.UTF_8)));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Không thể tạo token: {}", e.getMessage());
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể tạo token xác thực");
        }
    }

    // Tạo Refresh Token mới cho phiên đăng nhập mới
    public String generateRefreshToken(User user) {
        return generateRefreshToken(user, Instant.now());
    }

    // Tạo Refresh Token gắn liền với mốc thời gian bắt đầu phiên làm việc
    public String generateRefreshToken(User user, Instant sessionStart) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        Instant now = Instant.now();
        Instant effectiveSessionStart = (sessionStart != null) ? sessionStart : now;

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("QL_KyTucXa")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(refreshableDuration, ChronoUnit.SECONDS)))
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getUserId())
                .claim("tokenType", "REFRESH")
                .claim("sessionStart", effectiveSessionStart.getEpochSecond())
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes(StandardCharsets.UTF_8)));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Không thể tạo refresh token: {}", e.getMessage());
            throw new AppException(ErrorCode.OPERATION_FAILED, "Không thể tạo refresh token");
        }
    }

    // Xoay vòng Refresh Token: Giữ nguyên sessionStart ban đầu để bảo vệ giới hạn
    // tối đa của phiên
    public String rotateRefreshToken(User user, String oldToken) {
        Instant sessionStart;
        try {
            SignedJWT signedJWT = SignedJWT.parse(oldToken);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Object sessionStartClaim = claims.getClaim("sessionStart");
            if (sessionStartClaim instanceof Number num) {
                sessionStart = Instant.ofEpochSecond(num.longValue());
            } else if (sessionStartClaim instanceof String str && !str.isBlank()) {
                sessionStart = Instant.ofEpochSecond(Long.parseLong(str));
            } else {
                log.warn("Refresh token không chứa claim sessionStart");
                throw new AppException(ErrorCode.TOKEN_INVALID, "Refresh token không chứa thông tin phiên làm việc");
            }
        } catch (ParseException | NumberFormatException e) {
            log.error("Không thể phân tích refresh token cũ: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID, "Refresh token không hợp lệ");
        }
        return generateRefreshToken(user, sessionStart);
    }

    // Kiểm tra tính hợp lệ của token cho API checkToken
    public CheckToken_Response checkToken(CheckToken_Request request) throws JOSEException, ParseException {
        var token = request.getToken();

        // Tạo đối tượng kiểm tra chữ ký
        JWSVerifier jwsVerifier = new MACVerifier(signerKey.getBytes(StandardCharsets.UTF_8));
        SignedJWT jwt = SignedJWT.parse(token);

        // Refresh token không được dùng để authenticate tài nguyên, trả false
        Object tokenType = jwt.getJWTClaimsSet().getClaim("tokenType");
        if ("REFRESH".equalsIgnoreCase(tokenType != null ? tokenType.toString() : null)) {
            return CheckToken_Response.builder().checkToken(false).build();
        }

        Date expiryTime = jwt.getJWTClaimsSet().getExpirationTime();
        var verifiedok = jwt.verify(jwsVerifier);

        String jti = jwt.getJWTClaimsSet().getJWTID();
        boolean isLoggedOut = (jti != null && logoutTokenRepository.existsByToken(jti))
                || logoutTokenRepository.existsByToken(token);

        // Kiểm tra trạng thái tài khoản người dùng: Chặn ngay nếu tài khoản bị khóa/vô
        // hiệu hóa
        boolean isAccountActive = true;
        if (userRepository != null) {
            String username = jwt.getJWTClaimsSet().getSubject();
            if (username != null) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(userOpt.get().getStatus())) {
                    isAccountActive = false;
                }
            }
        }

        boolean isValid = verifiedok
                && expiryTime != null
                && expiryTime.after(new Date())
                && !isLoggedOut
                && isAccountActive;

        return CheckToken_Response.builder()
                .checkToken(isValid)
                .build();
    }

    // Xác thực tính hợp lệ của Token
    public SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes(StandardCharsets.UTF_8));
        SignedJWT signedJWT = SignedJWT.parse(token);

        var verified = signedJWT.verify(verifier);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        Date expiryTime = claims.getExpirationTime();

        if (!verified || expiryTime == null || !expiryTime.after(new Date())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Token không hợp lệ hoặc đã hết hạn");
        }

        // Kiểm tra loại token khớp với mục đích sử dụng
        Object tokenTypeClaim = claims.getClaim("tokenType");
        String tokenType = tokenTypeClaim != null ? tokenTypeClaim.toString() : null;
        boolean tokenIsRefresh = "REFRESH".equalsIgnoreCase(tokenType);

        if (isRefresh && !tokenIsRefresh) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Loại token không hợp lệ: yêu cầu Refresh Token");
        }
        if (!isRefresh && tokenIsRefresh) {
            throw new AppException(ErrorCode.UNAUTHENTICATED,
                    "Loại token không hợp lệ: không được dùng Refresh Token làm Access Token");
        }

        // Với refresh token: kiểm tra bắt buộc có sessionStart và giới hạn tuyệt đối
        // của phiên (8 giờ)
        if (isRefresh) {
            Instant sessionStartInstant = null;
            Object sessionStartClaim = claims.getClaim("sessionStart");
            if (sessionStartClaim instanceof Number num) {
                sessionStartInstant = Instant.ofEpochSecond(num.longValue());
            } else if (sessionStartClaim instanceof String str && !str.isBlank()) {
                try {
                    sessionStartInstant = Instant.ofEpochSecond(Long.parseLong(str));
                } catch (NumberFormatException e) {
                    log.warn("sessionStart trong refresh token không đúng định dạng số: {}", str);
                    throw new AppException(ErrorCode.UNAUTHENTICATED, "Refresh token thiếu thông tin phiên làm việc");
                }
            }

            if (sessionStartInstant == null) {
                log.warn("Refresh token không chứa claim sessionStart hợp lệ");
                throw new AppException(ErrorCode.UNAUTHENTICATED, "Refresh token thiếu thông tin phiên làm việc");
            }

            Instant maxSessionLimit = sessionStartInstant.plus(maxSessionDuration, ChronoUnit.SECONDS);
            if (Instant.now().isAfter(maxSessionLimit)) {
                log.warn("Phiên làm việc đã vượt quá giới hạn tối đa {} giây (sessionStart: {})",
                        maxSessionDuration, sessionStartInstant);
                throw new AppException(ErrorCode.UNAUTHENTICATED,
                        "Phiên làm việc đã quá thời hạn tối đa. Vui lòng đăng nhập lại!");
            }
        }

        // Với access token: jti nằm trong logout_tokens thì ném UNAUTHENTICATED
        if (!isRefresh) {
            String jti = claims.getJWTID();
            if ((jti != null && logoutTokenRepository.existsByToken(jti))
                    || logoutTokenRepository.existsByToken(token)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED, "Token đã bị vô hiệu hóa (đã đăng xuất)");
            }
        }

        // Kiểm tra tài khoản có bị khóa không
        if (userRepository != null) {
            String username = claims.getSubject();
            if (username != null) {
                Optional<User> userOpt = userRepository.findByUsername(username);
                if (userOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(userOpt.get().getStatus())) {
                    throw new AppException(ErrorCode.UNAUTHENTICATED,
                            "Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt");
                }
            }
        }

        return signedJWT;
    }

    // Lấy username (subject) từ token
    public String extractUsername(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            log.error("Không thể giải mã username từ token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    // Lấy userId từ claim của token
    public String extractUserId(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return (String) signedJWT.getJWTClaimsSet().getClaim("userId");
        } catch (ParseException e) {
            log.error("Không thể giải mã userId từ token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    // Lấy thời điểm hết hạn của token
    public Date extractExpiration(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getExpirationTime();
        } catch (ParseException e) {
            log.error("Không thể lấy hạn sử dụng từ token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    // Lấy JWT ID (jti)
    public String extractJwtId(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getJWTID();
        } catch (ParseException e) {
            log.error("Không thể lấy JWT ID từ token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    // Lấy scope từ token
    public String extractScope(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            Object scopeClaim = signedJWT.getJWTClaimsSet().getClaim("scope");
            return scopeClaim != null ? scopeClaim.toString() : "";
        } catch (ParseException e) {
            log.error("Không thể lấy scope từ token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
    }

    // Chuyển đổi Date sang LocalDateTime theo ZoneId mặc định của hệ thống
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null)
            return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // Cắt ngắn chuỗi an toàn theo độ dài tối đa
    public static String limit(String value, int max) {
        if (value == null)
            return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    // Xây dựng chuỗi Scope phân quyền ROLE_xxx cùng toàn bộ mã quyền của vai trò
    // trong vaiTroQuyen
    private String buildScope(User user) {
        Set<String> scopes = new LinkedHashSet<>();

        if (user.getRole() != null) {
            String rawRoleCode = user.getRole().getRoleCode();
            if (rawRoleCode != null && !rawRoleCode.isBlank()) {
                String formattedRole = rawRoleCode.trim().toUpperCase();
                if (!formattedRole.startsWith("ROLE_")) {
                    formattedRole = "ROLE_" + formattedRole;
                }
                scopes.add(formattedRole);
            }

            if (user.getRole().getRoleId() != null) {
                java.util.List<String> permissions = rolePermissionRepository
                        .findPermissionCodesByRoleId(user.getRole().getRoleId());
                if (permissions != null) {
                    scopes.addAll(permissions);
                }
            }
        }

        return String.join(" ", scopes);
    }
}
