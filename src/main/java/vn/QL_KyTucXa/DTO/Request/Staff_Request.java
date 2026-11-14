package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff_Request {

    // Khóa ngoại liên kết tài khoản: Tùy chọn (nếu để trống, hệ thống sẽ tự động tạo User với username = staffCode)
    private String userId;

    @NotBlank(message = "STAFF_CODE_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String staffCode;

    @NotBlank(message = "FULL_NAME_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String fullName;

    @Size(max = 100, message = "STRING_TOO_LONG")
    private String position;
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String department;

    @Pattern(regexp = "^$|^(0|\\+84)[0-9]{9}$", message = "PHONE_INVALID")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String phone;

    @Email(message = "EMAIL_INVALID")
    @Size(max = 150, message = "STRING_TOO_LONG")
    private String email;

    private LocalDate hireDate;

    private String status;
}