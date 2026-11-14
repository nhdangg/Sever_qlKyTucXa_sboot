package vn.QL_KyTucXa.DTO;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginVerificationSession {

    String verificationId;

    String userId;

    String username;

    String ipAddress;

    String userAgent;

    /**
     * Trạng thái phiên: PENDING, APPROVED, REJECTED, EXPIRED
     */
    @Builder.Default
    String status = "PENDING";

    String telegramChatId;

    Integer telegramMessageId;

    LocalDateTime createdAt;

    LocalDateTime expiresAt;

    Token_Response tokenResponse;

    @Builder.Default
    int failedAttempts = 0;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
