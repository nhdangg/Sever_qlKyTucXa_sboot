package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSelfUpdate_Request {

    @Past(message = "DOB_INVALID")
    private LocalDate dob;

    @Pattern(regexp = "^$|^[0-9]{12}$", message = "NATIONAL_ID_INVALID")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String nationalId;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String hometown;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String permanentAddress;

    @Pattern(regexp = "^$|^(0|\\+84)[0-9]{9}$", message = "PHONE_INVALID")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String phone;

    @Email(message = "EMAIL_INVALID")
    @Size(max = 150, message = "STRING_TOO_LONG")
    private String email;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String avatarUrl;

    @Size(max = 100, message = "STRING_TOO_LONG")
    private String emergencyContactName;

    @Size(max = 20, message = "STRING_TOO_LONG")
    private String emergencyContactPhone;

    @Size(max = 50, message = "STRING_TOO_LONG")
    private String emergencyContactRelation;
}

