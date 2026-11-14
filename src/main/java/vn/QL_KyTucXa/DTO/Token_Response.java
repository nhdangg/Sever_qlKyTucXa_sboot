package vn.QL_KyTucXa.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Token_Response {

    String token;
    String refreshToken;
    boolean authenticated;
    boolean requires2FA;
    boolean mustChangePassword;
    String verificationId;
    String status;
    String role;
    String roleName;
    String username;
    String fullName;
    String userCode;
    String message;
    Date expiryTime;
}
