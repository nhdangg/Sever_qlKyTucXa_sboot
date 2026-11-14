package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken_Request {

    @NotBlank(message = "EMAIL_BLANK")
    @Email(message = "EMAIL_INVALID")
    private String email;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String token;

    @Size(min = 6, message = "PASSWORD_INVALID")
    private String newPassword;
}