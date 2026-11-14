package vn.QL_KyTucXa.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.Entity.Role;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginVerificationServiceTest {

    @Mock
    private User_Repository userRepository;

    @Mock
    private AccountStatusPolicy accountStatusPolicy;

    @Mock
    private TokenResponseFactory tokenResponseFactory;

    @InjectMocks
    private LoginVerificationService loginVerificationService;

    private User adminUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder()
                .roleId("role-admin")
                .roleCode("ADMIN")
                .roleName("Quản trị viên")
                .build();

        adminUser = User.builder()
                .userId("user-admin")
                .username("admin")
                .status("ACTIVE")
                .role(adminRole)
                .build();
    }

    @Test
    @DisplayName("Tạo phiên xác thực 2FA thành công")
    void createSession_success() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "192.168.1.1", "Chrome", "123456789");

        assertThat(session).isNotNull();
        assertThat(session.getVerificationId()).isNotBlank();
        assertThat(session.getUsername()).isEqualTo("admin");
        assertThat(session.getStatus()).isEqualTo("PENDING");
        assertThat(session.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(session.getTelegramChatId()).isEqualTo("123456789");
    }

    @Test
    @DisplayName("Tạo phiên xác thực thất bại khi vượt quá giới hạn 3 phiên PENDING đang hoạt động")
    void createSession_exceedsMaxPendingSessions_throwsException() {
        loginVerificationService.createSession(adminUser, "127.0.0.1", "Browser", "123");
        loginVerificationService.createSession(adminUser, "127.0.0.1", "Browser", "123");
        loginVerificationService.createSession(adminUser, "127.0.0.1", "Browser", "123");

        AppException ex = assertThrows(AppException.class, () ->
                loginVerificationService.createSession(adminUser, "127.0.0.1", "Browser", "123"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("quá nhiều yêu cầu xác thực 2FA đang chờ");
    }

    @Test
    @DisplayName("Lấy phiên xác thực thành công khi ID hợp lệ")
    void getSession_success() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Firefox", "123456789");

        LoginVerificationSession retrieved = loginVerificationService.getSession(session.getVerificationId());

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getVerificationId()).isEqualTo(session.getVerificationId());
    }

    @Test
    @DisplayName("Lấy phiên xác thực thất bại khi không tìm thấy ID")
    void getSession_notFound() {
        AppException ex = assertThrows(AppException.class,
                () -> loginVerificationService.getSession("non-existent-id"));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_2FA_NOT_FOUND);
    }

    @Test
    @DisplayName("Admin bấm 'Đúng' (approve) thành công, cấp Token và lưu RefreshToken")
    void approve_success() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(tokenResponseFactory.issueSession(eq(adminUser), eq("127.0.0.1"), eq("Browser"), anyString()))
                .thenReturn(Token_Response.builder()
                        .token("access_token_admin")
                        .refreshToken("refresh_token_admin")
                        .authenticated(true)
                        .status("APPROVED")
                        .build());

        LoginVerificationSession approvedSession = loginVerificationService
                .approve(session.getVerificationId());

        assertThat(approvedSession).isNotNull();
        assertThat(approvedSession.getStatus()).isEqualTo("APPROVED");
        assertThat(approvedSession.getTokenResponse()).isNotNull();
        assertThat(approvedSession.getTokenResponse().isAuthenticated()).isTrue();
        verify(accountStatusPolicy).assertAccountUsable(adminUser);
        verify(tokenResponseFactory).issueSession(adminUser, "127.0.0.1", "Browser", "Đăng nhập thành công");
    }

    @Test
    @DisplayName("Duyệt 2FA khi tài khoản đã bị khóa: ném lỗi, không cấp token")
    void approve_userBanned_throwsException() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        doThrow(new AppException(ErrorCode.OPERATION_FAILED, "Tài khoản của bạn đã bị khóa hoặc chưa kích hoạt"))
                .when(accountStatusPolicy).assertAccountUsable(adminUser);

        AppException ex = assertThrows(AppException.class,
                () -> loginVerificationService.approve(session.getVerificationId()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        verify(tokenResponseFactory, never()).issueSession(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Admin bấm 'Sai' (reject) thành công, cập nhật trạng thái REJECTED")
    void reject_success() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");

        LoginVerificationSession rejectedSession = loginVerificationService.reject(session.getVerificationId());

        assertThat(rejectedSession).isNotNull();
        assertThat(rejectedSession.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Từ chối (reject) phiên đã được APPROVED sẽ bị chặn và ném lỗi")
    void reject_alreadyApproved_throwsException() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        when(tokenResponseFactory.issueSession(eq(adminUser), any(), any(), any()))
                .thenReturn(Token_Response.builder().token("tok").build());

        loginVerificationService.approve(session.getVerificationId());

        AppException ex = assertThrows(AppException.class,
                () -> loginVerificationService.reject(session.getVerificationId()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OPERATION_FAILED);
        assertThat(ex.getMessage()).contains("đã được phê duyệt thành công trước đó");
    }

    @Test
    @DisplayName("Từ chối (reject) phiên đã bị từ chối trước đó trả về phiên hiện tại mà không đổi trạng thái")
    void reject_alreadyRejected_returnsSameSession() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");

        loginVerificationService.reject(session.getVerificationId());
        LoginVerificationSession secondReject = loginVerificationService.reject(session.getVerificationId());

        assertThat(secondReject.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("Phê duyệt phiên đã bị từ chối sẽ ném ngoại lệ LOGIN_2FA_REJECTED")
    void approve_alreadyRejected_throwsException() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");
        loginVerificationService.reject(session.getVerificationId());

        AppException ex = assertThrows(AppException.class,
                () -> loginVerificationService.approve(session.getVerificationId()));
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_2FA_REJECTED);
    }

    @Test
    @DisplayName("Cập nhật message ID của Telegram thành công")
    void updateMessageId_success() {
        LoginVerificationSession session = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123456789");

        loginVerificationService.updateMessageId(session.getVerificationId(), 8888);

        assertThat(session.getTelegramMessageId()).isEqualTo(8888);
    }

    @Test
    @DisplayName("cleanupExpiredSessions xóa các phiên quá 30 phút khỏi bộ nhớ")
    void cleanupExpiredSessions_removesOldSessions() {
        LoginVerificationSession oldSession = loginVerificationService.createSession(
                adminUser, "127.0.0.1", "Browser", "123");
        oldSession.setCreatedAt(LocalDateTime.now().minusMinutes(35));

        loginVerificationService.cleanupExpiredSessions();

        assertThrows(AppException.class, () -> loginVerificationService.getSession(oldSession.getVerificationId()));
    }
}
