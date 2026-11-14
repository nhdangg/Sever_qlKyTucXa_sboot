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
public class LogoutToken_Response {
    String tokenId;
    // String token;
    String userId;
    String username;
    LocalDateTime expiresAt;
    String deviceInfo;
    String ipAddress;
    LocalDateTime logoutAt;
}
