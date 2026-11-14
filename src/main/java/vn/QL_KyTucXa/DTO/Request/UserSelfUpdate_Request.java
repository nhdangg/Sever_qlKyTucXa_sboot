package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Dữ liệu người dùng tự cập nhật tài khoản của chính mình (/api/users/me).
 * Không cho phép đổi vai trò, trạng thái hay tên đăng nhập.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSelfUpdate_Request {

    @NotBlank(message = "EMAIL_BLANK")
    @Email(message = "EMAIL_INVALID")
    @Size(max = 150, message = "STRING_TOO_LONG")
    private String email;

    @Pattern(regexp = "^$|^(0|\\+84)[0-9]{9}$", message = "PHONE_INVALID")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String phone;

    @Size(min = 6, message = "PASSWORD_INVALID")
    private String password;
}
