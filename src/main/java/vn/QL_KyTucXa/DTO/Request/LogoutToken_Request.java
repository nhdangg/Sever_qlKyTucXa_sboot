package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Size;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutToken_Request {

    @Size(max = 512, message = "STRING_TOO_LONG")
    private String token;
    private String refreshToken;

    private String userId;
    private LocalDateTime expiresAt;
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String deviceInfo;
    @Size(max = 45, message = "STRING_TOO_LONG")
    private String ipAddress;
}