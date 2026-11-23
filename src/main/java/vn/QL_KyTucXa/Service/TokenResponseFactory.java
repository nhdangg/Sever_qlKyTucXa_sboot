package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Entity.User;

import java.util.Date;

// Component xây dựng Token_Response chuẩn hóa từ UserProfileService
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenResponseFactory {

    UserProfileService userProfileService;
    Jwt_Service jwtService;
    RefreshTokenService refreshTokenService;

    // Cấp phiên đăng nhập mới (tạo Access Token, Refresh Token, lưu DB và trả
    // Token_Response)
    public Token_Response issueSession(User user, String ip, String userAgent, String message) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        Date expiryTime = jwtService.extractExpiration(accessToken);

        refreshTokenService.save(user, refreshToken, ip, userAgent);

        return buildTokenResponse(user, accessToken, refreshToken, expiryTime, message);
    }

    // Dựng Token_Response với đầy đủ thông tin định danh và vai trò lấy từ
    // UserProfileService
    public Token_Response buildTokenResponse(User user, String accessToken, String refreshToken, Date expiry,
            String message) {
        UserProfileDTO ho_so = userProfileService.resolveUserProfile(user);

        return Token_Response.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .authenticated(true)
                .expiryTime(expiry)
                .status("APPROVED")
                .requires2FA(false)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .role(ho_so.getRoleCode())
                .roleName(ho_so.getRoleName())
                .username(user.getUsername())
                .fullName(ho_so.getFullName())
                .userCode(ho_so.getUserCode())
                .message(message)
                .build();
    }
}
