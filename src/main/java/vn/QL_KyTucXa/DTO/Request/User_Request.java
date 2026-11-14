package vn.QL_KyTucXa.DTO.Request;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User_Request {

    @NotBlank(message = "USERNAME_BLANK")
    @Size(min = 3, max = 50, message = "USERNAME_INVALID")
    private String username;

    @Size(min = 6, message = "PASSWORD_INVALID")
    private String password;

    @NotBlank(message = "EMAIL_BLANK")
    @Email(message = "EMAIL_INVALID")
    @Size(max = 150, message = "STRING_TOO_LONG")
    private String email;

    @Pattern(regexp = "^$|^(0|\\+84)[0-9]{9}$", message = "PHONE_INVALID")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String phone;

    @Size(max = 50, message = "STRING_TOO_LONG")
    private String telegramChatId;

    @NotBlank(message = "ROLE_ID_BLANK")
    private String roleId;

    private String status;
}