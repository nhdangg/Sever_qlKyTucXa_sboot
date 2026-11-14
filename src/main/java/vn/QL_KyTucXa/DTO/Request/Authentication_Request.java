package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Authentication_Request {

    @NotBlank(message = "USERNAME_BLANK")
    private String username;

    @NotBlank(message = "PASSWORD_BLANK")
    private String password;

    // Mã dự phòng khẩn cấp khi dịch vụ Telegram gặp sự cố
    private String backupCode;
}