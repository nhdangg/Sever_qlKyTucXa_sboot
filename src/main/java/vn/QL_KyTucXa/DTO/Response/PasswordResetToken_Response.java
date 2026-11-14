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
public class PasswordResetToken_Response {
    String resetId;
    String userId;
    String username;

    // String token;
    LocalDateTime expiresAt;
    Boolean isUsed;
    LocalDateTime createdAt;
}