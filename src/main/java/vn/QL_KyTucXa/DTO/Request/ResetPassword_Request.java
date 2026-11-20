package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// DTO yêu cầu đặt lại mật khẩu người dùng dành cho Quản trị viên
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResetPassword_Request {

    @Size(min = 6, message = "PASSWORD_INVALID")
    String newPassword;
}
