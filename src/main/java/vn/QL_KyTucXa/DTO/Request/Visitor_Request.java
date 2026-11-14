package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Visitor_Request {

    @NotBlank(message = "VISITOR_NAME_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String visitorName;

    @Size(max = 20, message = "STRING_TOO_LONG")
    private String nationalId;
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String phone;

    private String hostStudentId;

    private String roomId;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String purpose;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String idCardImageUrl;
    private String approvedBy;
}