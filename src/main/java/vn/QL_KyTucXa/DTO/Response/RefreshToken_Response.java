package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefreshToken_Response {
    String tokenId;
    String userId;
    String username;
    // String token;
    String deviceInfo;
    String ipAddress;
    LocalDateTime issuedAt;
    LocalDateTime expiresAt;
    Boolean isRevoked;
    LocalDateTime revokedAt;
}