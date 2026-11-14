package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.Entity.RefreshToken;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Repository.RefreshToken_Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

// Service quản lý vòng đời và lưu trữ Refresh Token trong CSDL (bảng tokenLamMoi)
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenService {

    RefreshToken_Repository refreshTokenRepository;
    Jwt_Service jwtService;
    CurrentUser_Service currentUserService;

    // Lưu Refresh Token vào CSDL (băm SHA-256 để tránh vượt quá 512 ký tự và bảo mật CSDL)
    @Transactional
    public RefreshToken save(User user, String token, String ip, String userAgent) {
        Date exp = jwtService.extractExpiration(token);
        String hashedToken = hashToken(token);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(hashedToken)
                .expiresAt(Jwt_Service.toLocalDateTime(exp))
                .ipAddress(Jwt_Service.limit(ip, 45))
                .deviceInfo(Jwt_Service.limit(userAgent, 255))
                .isRevoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    // Tìm Refresh Token theo mã băm SHA-256 (khóa bi quan PESSIMISTIC_WRITE yêu cầu transaction bắt buộc)
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(hashToken(token));
    }

    // Xóa Refresh Token khi người dùng logout có kiểm tra quyền sở hữu của người đăng xuất
    @Transactional
    public void deleteIfExists(String token, String expectedUserId) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenRepository.findByToken(hashToken(token)).ifPresent(storedToken -> {
            if (expectedUserId != null && storedToken.getUser() != null
                    && !expectedUserId.equals(storedToken.getUser().getUserId())) {
                log.warn("Cảnh báo bảo mật: Từ chối xóa Refresh Token vì không thuộc quyền sở hữu của người đăng xuất (userId={})", expectedUserId);
                return;
            }
            refreshTokenRepository.delete(storedToken);
            log.info("Đã xóa Refresh Token của người dùng {}", storedToken.getUser() != null ? storedToken.getUser().getUsername() : "null");
        });
    }

    // Xóa Refresh Token khi người dùng logout (tự động kiểm tra người dùng hiện tại từ Security Context nếu có)
    @Transactional
    public void deleteIfExists(String token) {
        String currentUserId = resolveCurrentUserId();
        deleteIfExists(token, currentUserId);
    }

    // Thu hồi toàn bộ các phiên đang hoạt động của người dùng
    @Transactional
    public int revokeAllActiveByUserId(String userId) {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.revokeAllActiveByUserId(userId, now);
    }

    // Lưu thực thể RefreshToken (tự động băm SHA-256 chuỗi token nếu chưa băm)
    @Transactional
    public RefreshToken save(RefreshToken refreshToken) {
        if (refreshToken != null && refreshToken.getToken() != null) {
            refreshToken.setToken(hashToken(refreshToken.getToken()));
        }
        return refreshTokenRepository.save(refreshToken);
    }

    // Lấy ID người dùng đăng nhập hiện tại từ Security Context nếu có
    private String resolveCurrentUserId() {
        if (currentUserService != null) {
            try {
                return currentUserService.userId();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // Dọn dẹp các Refresh Token đã hết hạn khỏi CSDL (chạy định kỳ)
    @Transactional
    public int deleteExpiredTokens() {
        return refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
    }

    // Băm chuỗi token bằng SHA-256 (độ dài luôn cố định 64 ký tự hex)
    public static String hashToken(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        if (token.length() == 64 && token.matches("^[a-fA-F0-9]{64}$")) {
            return token.toLowerCase();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không tìm thấy thuật toán băm SHA-256", e);
        }
    }
}
