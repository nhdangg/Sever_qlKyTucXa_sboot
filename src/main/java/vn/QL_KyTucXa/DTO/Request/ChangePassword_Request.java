package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePassword_Request {

    @NotBlank(message = "PASSWORD_BLANK")
    String currentPassword;

    @NotBlank(message = "PASSWORD_BLANK")
    @Size(min = 6, message = "PASSWORD_INVALID")
    String newPassword;
}

