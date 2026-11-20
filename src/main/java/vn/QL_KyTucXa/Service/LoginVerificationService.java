package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.LoginVerificationSession;
import vn.QL_KyTucXa.DTO.Token_Response;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Exception.AppException;
import vn.QL_KyTucXa.Exception.ErrorCode;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Quản lý phiên xác thực đăng nhập 2FA qua Telegram
// Phiên 2FA được quản lý trong bộ nhớ (ConcurrentHashMap) phù hợp với môi trường single-instance
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LoginVerificationService {

    private static final int MAX_PENDING_SESSIONS_PER_USER = 3;

    User_Repository userRepository;
    AccountStatusPolicy accountStatusPolicy;
    TokenResponseFactory tokenResponseFactory;

    Map<String, LoginVerificationSession> sessions = new ConcurrentHashMap<>();

    // Tạo phiên xác thực đăng nhập 2FA cho Admin
    public LoginVerificationSession createSession(User user, String ipAddress, String userAgent,
            String telegramChatId) {
        // Dọn dẹp trạng thái các phiên đã hết hạn của người dùng này
        sessions.values().stream()
                .filter(s -> user.getUserId().equals(s.getUserId()) && s.isExpired()
                        && "PENDING".equalsIgnoreCase(s.getStatus()))
                .forEach(s -> s.setStatus("EXPIRED"));

        // Giới hạn tối đa số phiên PENDING đang hoạt động của mỗi tài khoản để chống
        // spam tin nhắn Telegram
        long pendingCount = sessions.values().stream()
                .filter(s -> user.getUserId().equals(s.getUserId())
                        && "PENDING".equalsIgnoreCase(s.getStatus())
                        && !s.isExpired())
                .count();

        if (pendingCount >= MAX_PENDING_SESSIONS_PER_USER) {
            log.warn("Tài khoản {} vượt quá giới hạn {} phiên 2FA PENDING", user.getUsername(),
                    MAX_PENDING_SESSIONS_PER_USER);
            throw new AppException(ErrorCode.OPERATION_FAILED,
                    "Bạn đang có quá nhiều yêu cầu xác thực 2FA đang chờ. Vui lòng hoàn tất hoặc đợi các yêu cầu trước hết hạn");
        }

        String verificationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        LoginVerificationSession session = LoginVerificationSession.builder()
                .verificationId(verificationId)
                .userId(user.getUserId())
                .username(user.getUsername())
                .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                .userAgent(userAgent != null ? userAgent : "Trình duyệt")
                .status("PENDING")
                .telegramChatId(telegramChatId)
                .createdAt(now)
                .expiresAt(now.plusMinutes(5)) // Hết hạn sau 5 phút
                .build();

        sessions.put(verificationId, session);
        log.info("Đã tạo phiên xác thực 2FA: ID={}, User={}", verificationId, user.getUsername());
        return session;
    }

    // Lấy thông tin phiên xác thực
    public LoginVerificationSession getSession(String verificationId) {
        LoginVerificationSession session = sessions.get(verificationId);
        if (session == null) {
            throw new AppException(ErrorCode.LOGIN_2FA_NOT_FOUND);
        }

        if (session.isExpired() && "PENDING".equalsIgnoreCase(session.getStatus())) {
            session.setStatus("EXPIRED");
        }

        return session;
    }

    // Phê duyệt đăng nhập (khi Admin nhấn 'Đúng' trên Telegram)
    @Transactional
    public LoginVerificationSession approve(String verificationId) {
        LoginVerificationSession session = getSession(verificationId);

        synchronized (session) {
            if ("APPROVED".equalsIgnoreCase(session.getStatus())) {
                log.info("Phiên xác thực {} đã được phê duyệt trước đó", verificationId);
                return session;
            }

            if (session.isExpired()) {
                session.setStatus("EXPIRED");
                throw new AppException(ErrorCode.LOGIN_2FA_EXPIRED);
            }

            if ("REJECTED".equalsIgnoreCase(session.getStatus())) {
                throw new AppException(ErrorCode.LOGIN_2FA_REJECTED);
            }

            // Lấy thông tin User và tạo token
            User nguoi_dung = userRepository.findById(session.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            // Kiểm tra tài khoản có bị khóa/inactive trong thời gian chờ duyệt không
            accountStatusPolicy.assertAccountUsable(nguoi_dung);

            Token_Response tokenResponse = tokenResponseFactory.issueSession(
                    nguoi_dung, session.getIpAddress(), session.getUserAgent(), "Đăng nhập thành công");

            session.setStatus("APPROVED");
            session.setTokenResponse(tokenResponse);
            log.info("Phiên xác thực {} đã được Admin phê duyệt thành công!", verificationId);
            return session;
        }
    }

    // Từ chối đăng nhập (khi Admin nhấn 'Sai' trên Telegram)
    public LoginVerificationSession reject(String verificationId) {
        LoginVerificationSession session = getSession(verificationId);

        synchronized (session) {
            // Chặn lật ngược trạng thái sau khi phiên đã được phê duyệt
            if ("APPROVED".equalsIgnoreCase(session.getStatus())) {
                log.warn("Không thể từ chối phiên xác thực {} vì đã được phê duyệt trước đó", verificationId);
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Phiên đăng nhập đã được phê duyệt thành công trước đó");
            }

            if ("REJECTED".equalsIgnoreCase(session.getStatus())) {
                log.info("Phiên xác thực {} đã bị từ chối trước đó", verificationId);
                return session;
            }

            if (session.isExpired()) {
                session.setStatus("EXPIRED");
                throw new AppException(ErrorCode.LOGIN_2FA_EXPIRED);
            }

            session.setStatus("REJECTED");
            log.warn("Phiên xác thực {} đã bị Admin từ chối đăng nhập!", verificationId);
            return session;
        }
    }

    // Cập nhật telegramMessageId cho phiên xác thực để sửa tin nhắn
    public void updateMessageId(String verificationId, Integer messageId) {
        LoginVerificationSession session = sessions.get(verificationId);
        if (session != null) {
            session.setTelegramMessageId(messageId);
        }
    }

    // Hủy và xóa phiên xác thực 2FA khi xảy ra lỗi gửi tin nhắn Telegram (chống khóa tài khoản 5 phút do lỗi mạng)
    public void cancelSession(String verificationId) {
        if (verificationId != null) {
            sessions.remove(verificationId);
            log.info("Đã xóa phiên xác thực 2FA do gặp lỗi gửi Telegram: {}", verificationId);
        }
    }

    // Phê duyệt đăng nhập bằng mã dự phòng khẩn cấp khi Telegram gặp sự cố
    @Transactional
    public LoginVerificationSession approveWithBackupCode(String verificationId, String providedCode, String expectedCode) {
        LoginVerificationSession session = getSession(verificationId);

        synchronized (session) {
            if ("REJECTED".equalsIgnoreCase(session.getStatus())) {
                throw new AppException(ErrorCode.LOGIN_2FA_REJECTED,
                        "Phiên xác thực đã bị từ chối hoặc bị khóa do nhập sai mã dự phòng quá số lần quy định");
            }
            if (session.isExpired()) {
                session.setStatus("EXPIRED");
                throw new AppException(ErrorCode.LOGIN_2FA_EXPIRED);
            }
            if ("APPROVED".equalsIgnoreCase(session.getStatus())) {
                return session;
            }

            if (expectedCode == null || expectedCode.isBlank()
                    || providedCode == null || !expectedCode.trim().equals(providedCode.trim())) {
                session.setFailedAttempts(session.getFailedAttempts() + 1);

                // Đồng thời tăng số lần thất bại failedLoginAttempts trên User
                if (session.getUserId() != null) {
                    userRepository.findById(session.getUserId()).ifPresent(u -> {
                        int fails = u.getFailedLoginAttempts() != null ? u.getFailedLoginAttempts() : 0;
                        u.setFailedLoginAttempts(fails + 1);
                        userRepository.save(u);
                    });
                }

                if (session.getFailedAttempts() >= 3) {
                    session.setStatus("REJECTED");
                    log.warn("[2FA SECURITY] Phiên xác thực {} bị KHÓA do nhập sai mã dự phòng quá 3 lần!", verificationId);
                    throw new AppException(ErrorCode.OPERATION_FAILED,
                            "Bạn đã nhập sai mã dự phòng quá 3 lần. Phiên xác thực 2FA đã bị hủy.");
                }

                int remaining = 3 - session.getFailedAttempts();
                throw new AppException(ErrorCode.OPERATION_FAILED,
                        "Mã xác thực dự phòng không chính xác. Bạn còn " + remaining + " lần thử.");
            }

            log.warn("Phiên xác thực {} được phê duyệt bằng MÃ DỰ PHÒNG KHẨN CẤP (BACKUP CODE)!", verificationId);
            return approve(verificationId);
        }
    }

    // Định kỳ dọn dẹp các phiên đã quá 30 phút khỏi bộ nhớ
    @Scheduled(fixedRate = 600000) // 10 phút/lần
    public void cleanupExpiredSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        sessions.entrySet().removeIf(entry -> entry.getValue().getCreatedAt() != null
                && entry.getValue().getCreatedAt().isBefore(threshold));
    }
}
