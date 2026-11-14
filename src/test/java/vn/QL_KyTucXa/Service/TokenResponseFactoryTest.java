package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.DTO.UserProfileDTO;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenResponseFactoryTest {

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private Jwt_Service jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private TokenResponseFactory tokenResponseFactory;

    @Test
    @DisplayName("buildTokenResponse dựng đầy đủ thông tin từ UserProfileService, không hardcode")
    void buildTokenResponse_success() {
        Role role = Role.builder().roleId("role-01").roleCode("STAFF").roleName("Nhân viên").build();
        User user = User.builder().userId("u-01").username("staff01").role(role).build();
        Date expiry = new Date(System.currentTimeMillis() + 900_000);

        when(userProfileService.resolveUserProfile(user)).thenReturn(
                UserProfileDTO.builder()
                        .roleCode("STAFF")
                        .roleName("Nhân viên")
                        .fullName("Trần Thị B")
                        .userCode("NV001")
                        .build()
        );

        Token_Response response = tokenResponseFactory.buildTokenResponse(
                user, "access_token", "refresh_token", expiry, "Đăng nhập thành công");

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access_token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.isRequires2FA()).isFalse();
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getExpiryTime()).isEqualTo(expiry);
        assertThat(response.getUsername()).isEqualTo("staff01");
        assertThat(response.getFullName()).isEqualTo("Trần Thị B");
        assertThat(response.getUserCode()).isEqualTo("NV001");
        assertThat(response.getRole()).isEqualTo("STAFF");
        assertThat(response.getRoleName()).isEqualTo("Nhân viên");
        assertThat(response.getMessage()).isEqualTo("Đăng nhập thành công");
    }

    @Test
    @DisplayName("issueSession tạo access token, refresh token, lưu DB và trả về Token_Response")
    void issueSession_createsAndSavesTokens() {
        Role role = Role.builder().roleId("role-01").roleCode("STAFF").roleName("Nhân viên").build();
        User user = User.builder().userId("u-01").username("staff01").role(role).build();
        Date expiry = new Date(System.currentTimeMillis() + 900_000);

        when(jwtService.generateToken(user)).thenReturn("access_token_123");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh_token_456");
        when(jwtService.extractExpiration("access_token_123")).thenReturn(expiry);
        when(userProfileService.resolveUserProfile(user)).thenReturn(
                UserProfileDTO.builder()
                        .roleCode("STAFF")
                        .roleName("Nhân viên")
                        .fullName("Trần Thị B")
                        .userCode("NV001")
                        .build()
        );

        Token_Response response = tokenResponseFactory.issueSession(
                user, "192.168.1.1", "Mozilla/5.0", "Đăng nhập thành công");

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("access_token_123");
        assertThat(response.getRefreshToken()).isEqualTo("refresh_token_456");
        assertThat(response.isAuthenticated()).isTrue();
        org.mockito.Mockito.verify(refreshTokenService).save(user, "refresh_token_456", "192.168.1.1", "Mozilla/5.0");
    }
}
