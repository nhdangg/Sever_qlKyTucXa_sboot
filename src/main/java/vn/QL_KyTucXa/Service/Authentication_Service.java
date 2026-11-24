package vn.QL_KyTucXa.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.QL_KyTucXa.Config.TelegramProperties;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;
import vn.QL_KyTucXa.DTO.Request.Authentication_Request;
import vn.QL_KyTucXa.DTO.Request.LogoutToken_Request;
import vn.QL_KyTucXa.DTO.Request.RefreshToken_Request;
import vn.QL_KyTucXa.DTO.Request.User_Request;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.Entity.LogoutToken;
import vn.QL_KyTucXa.Entity.RefreshToken;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.LogoutToken_Repository;
import vn.QL_KyTucXa.Repository.Role_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Date;

// Service xử lý xác thực, đăng nhập, phân quyền, cấp phát và thu hồi Token theo mô hình xoay vòng
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Authentication_Service {

    User_Repository userRepository;
    LogoutToken_Repository logoutTokenRepository;
    RefreshTokenService refreshTokenService;
    Role_Repository roleRepository;
    PasswordEncoder passwordEncoder;
    TelegramProperties telegramProperties;

    Jwt_Service tokenService;
    LoginVerificationService twoFactorAuthService;
    TelegramService notificationService;
    RoleBasedTwoFactorPolicy twoFactorPolicy;
    AccountStatusPolicy accountStatusPolicy;
    HttpRequestClientInfoResolver clientInfoResolver;
    TokenResponseFactory tokenResponseFactory;
    User_Service userService;
    org.springframework.core.env.Environment environment;

    @NonFinal
    @Value("${jwt.reuse-grace-seconds:10}")
    long reuseGraceSeconds = 10;
    public static final int maxMkFail = 5;
    public static final int Lock_Hours = 12;

    // Xác thực thông tin đăng nhập và cấp token (hoặc kích hoạt 2FA nếu được yêu cầu)
    @Transactional(noRollbackFor = AppException.class)
    public Token_Response login(Authentication_Request request) {
        log.info("Xử lý đăng nhập cho tài khoản: {}", request.getUsername());

        User nguoi_dung = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        // Mở khóa sau 12 tiếng từ lúc cập nhật khóa tài khoản
        if ("LOCKED".equalsIgnoreCase(nguoi_dung.getStatus()) && nguoi_dung.getFailedLoginAttempts() != null
                && nguoi_dung.getFailedLoginAttempts() >= maxMkFail) {
            LocalDateTime LockTime = nguoi_dung.getUpdatedAt();
            LocalDateTime unlockTime = LockTime.plusHours(Lock_Hours);
            if (LocalDateTime.now().isAfter(unlockTime)) {
                nguoi_dung.setStatus("ACTIVE");
                nguoi_dung.setFailedLoginAttempts(0);
                userRepository.save(nguoi_dung);
            } else {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
        }
        // Kiểm tra trạng thái tài khoản
        accountStatusPolicy.assertAccountUsable(nguoi_dung);

        // Kiểm tra mật khẩu đăng nhập
        boolean authenticated = passwordEncoder.matches(request.getPassword(), nguoi_dung.getPasswordHash());
        if (!authenticated) {
            int attemps = ((nguoi_dung.getFailedLoginAttempts() != null) ? nguoi_dung.getFailedLoginAttempts() : 0) + 1;
            nguoi_dung.setFailedLoginAttempts(attemps);
            // Khóa tài khoản sau khi nhập sai 5 lần liên tiếp
            if (attemps >= maxMkFail) {
                nguoi_dung.setStatus("LOCKED");
                userRepository.save(nguoi_dung);

                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
            userRepository.save(nguoi_dung);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Đăng nhập đúng thông tin: đặt lại số lần sai về 0
        nguoi_dung.setFailedLoginAttempts(0);
        nguoi_dung.setLastLoginAt(LocalDateTime.now());
        userRepository.save(nguoi_dung);

        // Kiểm tra yêu cầu 2FA đối với tài khoản quản trị (RoleBasedTwoFactorPolicy)
        if (twoFactorPolicy.requiresTwoFactorAuth(nguoi_dung)) {
            // 1. Phương án dự phòng khẩn cấp: nếu người dùng gửi kèm backupCode hợp lệ
            if (request.getBackupCode() != null && !request.getBackupCode().isBlank()) {
                String expectedBackup = telegramProperties.getBackupCode();
                if (expectedBackup != null && !expectedBackup.isBlank()
                        && expectedBackup.trim().equals(request.getBackupCode().trim())) {
                    log.warn("[2FA BACKUP] Quản trị viên '{}' đăng nhập thành công bằng Mã dự phòng khẩn cấp!",
                            nguoi_dung.getUsername());
                    return tokenResponseFactory.issueSession(nguoi_dung,
                            clientInfoResolver.getClientIpAddress(),
                            clientInfoResolver.getClientUserAgent(),
                            "Đăng nhập thành công bằng mã xác thực dự phòng");
                } else {
                    int fails = nguoi_dung.getFailedLoginAttempts() != null ? nguoi_dung.getFailedLoginAttempts() : 0;
                    nguoi_dung.setFailedLoginAttempts(fails + 1);
                    userRepository.save(nguoi_dung);
                    throw new AppException(ErrorCode.OPERATION_FAILED, "Mã dự phòng khẩn cấp không chính xác");
                }
            }

            // 2. Không được fail-open: Luôn kích hoạt quy trình 2FA
            String targetChatId = (nguoi_dung.getTelegramChatId() != null && !nguoi_dung.getTelegramChatId().isBlank())
                    ? nguoi_dung.getTelegramChatId().trim()
                    : telegramProperties.getAdminChatId();

            String ipAddress = clientInfoResolver.getClientIpAddress();
            String userAgent = clientInfoResolver.getClientUserAgent();

            LoginVerificationSession session = twoFactorAuthService.createSession(nguoi_dung, ipAddress, userAgent,
                    targetChatId);

            try {
                Integer messageId = notificationService.sendLoginVerificationMessage(session, targetChatId);
                boolean isDev = environment != null && environment
                        .acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "test", "default"));
                if (messageId == null && !isDev) {
                    log.error("Không thể gửi thông báo xác thực Telegram cho tài khoản {}", nguoi_dung.getUsername());
                    twoFactorAuthService.cancelSession(session.getVerificationId());
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Không thể gửi tin nhắn xác thực 2FA qua Telegram. Vui lòng thử lại sau hoặc sử dụng mã dự phòng.");
                }
                if (messageId != null) {
                    twoFactorAuthService.updateMessageId(session.getVerificationId(), messageId);
                }
            } catch (Exception ex) {
                // Xóa phiên PENDING ngay lập tức để không bị tích lũy thành 3 lần tự khóa 5
                // phút
                twoFactorAuthService.cancelSession(session.getVerificationId());
                if (ex instanceof AppException) {
                    throw ex;
                }
                log.error("Lỗi khi gửi xác thực Telegram: {}", ex.getMessage());
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Không thể gửi tin nhắn xác thực 2FA qua Telegram: " + ex.getMessage());
            }

            return Token_Response.builder()
                    .authenticated(false)
                    .requires2FA(true)
                    .verificationId(session.getVerificationId())
                    .status("PENDING")
                    .message("Yêu cầu xác thực 2FA đã được gửi đến Telegram của Quản trị viên")
                    .build();
        }

        // Cấp Access Token và Refresh Token mới cho phiên đăng nhập thông thường
        return tokenResponseFactory.issueSession(nguoi_dung,
                clientInfoResolver.getClientIpAddress(),
                clientInfoResolver.getClientUserAgent(),
                "Đăng nhập thành công");
    }

    // Làm mới Access Token thông qua Refresh Token theo mô hình xoay vòng
    // (Rotation)
    @Transactional(noRollbackFor = AppException.class)
    public Token_Response refreshToken(RefreshToken_Request request) {
        log.info("Xử lý cấp mới token từ refresh token");

        // Kiểm tra chữ ký, hạn dùng, loại token, giới hạn 8h và jti blacklist
        SignedJWT signedJWT;
        try {
            signedJWT = tokenService.verifyToken(request.getToken(), true);
        } catch (JOSEException | ParseException e) {
            log.error("Lỗi khi xác thực refresh token: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        // Lấy username từ subject và tìm người dùng trong hệ thống
        String username;
        try {
            username = signedJWT.getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        User nguoi_dung = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // Kiểm tra trạng thái tài khoản
        accountStatusPolicy.assertAccountUsable(nguoi_dung);

        // Tìm RefreshToken theo chuỗi token (khóa bi quan chống race condition)
        RefreshToken ma_lam_moi = refreshTokenService.findByToken(request.getToken())
                .orElseThrow(
                        () -> new AppException(ErrorCode.TOKEN_INVALID, "Refresh token không tồn tại trong hệ thống"));

        LocalDateTime now = LocalDateTime.now();

        // Nếu isRevoked = true: token đã xoay vòng bị dùng lại -> kiểm tra khoảng ân hạn
        if (Boolean.TRUE.equals(ma_lam_moi.getIsRevoked())) {
            boolean recentlyRotated = ma_lam_moi.getRevokedAt() != null
                    && ma_lam_moi.getRevokedAt().isAfter(now.minusSeconds(reuseGraceSeconds));
            if (recentlyRotated) {
                throw new AppException(ErrorCode.TOKEN_INVALID, "Yêu cầu làm mới đang được xử lý, vui lòng thử lại");
            }
            int revokedCount = refreshTokenService.revokeAllActiveByUserId(nguoi_dung.getUserId());
            log.warn("Tái sử dụng refresh token của '{}', đã thu hồi {} phiên", username, revokedCount);
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại");
        }

        // Chuẩn bị dữ liệu token mới trước khi ghi vào CSDL
        String newAccess = tokenService.generateToken(nguoi_dung);
        String newRefresh = tokenService.rotateRefreshToken(nguoi_dung, request.getToken());
        Date accessExp = tokenService.extractExpiration(newAccess);
        Token_Response tokenResponse = tokenResponseFactory.buildTokenResponse(nguoi_dung, newAccess, newRefresh,
                accessExp,
                "Làm mới phiên đăng nhập thành công");

        // Ghi thông tin thu hồi và lưu token mới vào CSDL
        ma_lam_moi.setIsRevoked(true);
        ma_lam_moi.setRevokedAt(now);
        refreshTokenService.save(ma_lam_moi);
        saveRefreshToken(nguoi_dung, newRefresh);

        return tokenResponse;
    }

    // Đăng xuất khỏi hệ thống: Thu hồi refresh token và đưa jti access token vào blacklist
    @Transactional
    public void logout(LogoutToken_Request request) {
        log.info("Xử lý đăng xuất tài khoản");
        LocalDateTime now = LocalDateTime.now();
        String currentUserId = null;

        String rawToken = (request != null) ? request.getToken() : null;
        if (!StringUtils.hasText(rawToken)) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest httpRequest = attributes.getRequest();
                String authHeader = httpRequest.getHeader("Authorization");
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    rawToken = authHeader.substring(7);
                }
            }
        }

        // Nếu có access token: đưa jti vào blacklist và trích xuất userId của người đang logout
        if (StringUtils.hasText(rawToken)) {
            try {
                SignedJWT signedJWT = null;
                Date expiryTime = null;
                String jti = null;

                try {
                    signedJWT = tokenService.verifyToken(rawToken, false);
                    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                    jti = claims.getJWTID();
                    expiryTime = claims.getExpirationTime();
                    currentUserId = (String) claims.getClaim("userId");
                } catch (Exception ex) {
                    // Nếu verifyToken không pass (ví dụ access token vừa hết hạn), vẫn parse payload để lấy jti blacklist
                    try {
                        signedJWT = SignedJWT.parse(rawToken);
                        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
                        jti = claims.getJWTID();
                        expiryTime = claims.getExpirationTime();
                        currentUserId = (String) claims.getClaim("userId");
                    } catch (Exception ignored) {
                    }
                }

                User nguoi_dung = (StringUtils.hasText(currentUserId))
                        ? userRepository.findById(currentUserId).orElse(null)
                        : null;

                // Kiểm tra existsByToken trước khi lưu để tránh vi phạm Unique Constraint khi có 2 request logout đồng thời
                if (StringUtils.hasText(jti) && !logoutTokenRepository.existsByToken(jti)) {
                    LocalDateTime expAt = (expiryTime != null)
                            ? Jwt_Service.toLocalDateTime(expiryTime)
                            : now.plusMinutes(15);
                    if (expAt.isBefore(now)) {
                        expAt = now.plusMinutes(15);
                    }

                    LogoutToken token_dang_xuat = LogoutToken.builder()
                            .tokenId(jti)
                            .token(jti)
                            .user(nguoi_dung)
                            .expiresAt(expAt)
                            .deviceInfo(Jwt_Service.limit(clientInfoResolver.getClientUserAgent(), 255))
                            .ipAddress(Jwt_Service.limit(clientInfoResolver.getClientIpAddress(), 45))
                            .logoutAt(now)
                            .build();

                    logoutTokenRepository.save(token_dang_xuat);
                }
            } catch (Exception e) {
                log.info("Bỏ qua access token khi logout: {}", e.getMessage());
            }
        }

        // Thu hồi refresh token trong refresh_tokens: chỉ xóa nếu thuộc quyền sở hữu của người đang logout
        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            if (currentUserId != null) {
                refreshTokenService.deleteIfExists(request.getRefreshToken(), currentUserId);
            } else {
                refreshTokenService.deleteIfExists(request.getRefreshToken());
            }
        }
    }

    // Helper lưu Refresh Token vào CSDL thông qua client info của request hiện tại
    private void saveRefreshToken(User user, String token) {
        refreshTokenService.save(user, token,
                clientInfoResolver.getClientIpAddress(),
                clientInfoResolver.getClientUserAgent());
    }

    // Đăng ký tài khoản người dùng mới
    @Transactional
    public User_Response register(User_Request request) {
        log.info("Đăng ký tài khoản người dùng mới: {}", request.getUsername());
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED, "UserName đã tồn tại hãy thay đổi UserName khác.");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new AppException(ErrorCode.PASSWORD_BLANK);
        }
        // Ghi đè các trường nhạy cảm do client gửi lên
        request.setRoleId(roleRepository.findByRoleCode("STUDENT")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND))
                .getRoleId());
        request.setStatus("ACTIVE");
        request.setTelegramChatId(null);
        return userService.createFromRegistration(request);
    }

    // Kiểm tra trạng thái phê duyệt từ Telegram và xóa tokenResponse khỏi session
    public Token_Response checkLoginStatus(String verificationId) {
        LoginVerificationSession session = twoFactorAuthService.getSession(verificationId);

        if ("APPROVED".equalsIgnoreCase(session.getStatus())) {
            Token_Response tokenResponse = session.getTokenResponse();
            if (tokenResponse == null) {
                throw new AppException(ErrorCode.LOGIN_2FA_EXPIRED,
                        "Phiên xác thực 2FA đã được sử dụng hoặc đã hết hạn");
            }
            session.setTokenResponse(null);
            return tokenResponse;
        } else if ("REJECTED".equalsIgnoreCase(session.getStatus())) {
            throw new AppException(ErrorCode.LOGIN_2FA_REJECTED);
        } else if ("EXPIRED".equalsIgnoreCase(session.getStatus()) || session.isExpired()) {
            throw new AppException(ErrorCode.LOGIN_2FA_EXPIRED);
        }

        return Token_Response.builder()
                .authenticated(false)
                .requires2FA(true)
                .verificationId(session.getVerificationId())
                .status("PENDING")
                .message("Đang chờ Quản trị viên xác nhận trên Telegram...")
                .build();
    }

    // Phê duyệt hoặc từ chối đăng nhập (dùng cho Webhook hoặc Link xác nhận trực tiếp)
    public String confirmLoginFromTelegram(String verificationId, String action) {
        if ("APPROVE".equalsIgnoreCase(action) || "YES".equalsIgnoreCase(action) || "DUNG".equalsIgnoreCase(action)) {
            twoFactorAuthService.approve(verificationId);
            return "APPROVED";
        } else {
            twoFactorAuthService.reject(verificationId);
            return "REJECTED";
        }
    }

    // Phê duyệt đăng nhập 2FA bằng mã dự phòng khẩn cấp khi Telegram gặp sự cố
    @Transactional
    public Token_Response verifyWithBackupCode(String verificationId, String backupCode) {
        String expectedBackup = telegramProperties.getBackupCode();
        LoginVerificationSession session = twoFactorAuthService.approveWithBackupCode(verificationId, backupCode,
                expectedBackup);
        return session.getTokenResponse();
    }
}
