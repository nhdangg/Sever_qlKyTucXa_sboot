package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationPeriod_Request {

    @NotBlank(message = "PERIOD_NAME_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String periodName;

    @NotBlank(message = "ACADEMIC_YEAR_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String academicYear;

    @NotNull(message = "START_DATE_BLANK")
    private LocalDate startDate;

    @NotNull(message = "END_DATE_BLANK")
    private LocalDate endDate;

    private LocalDate registrationDeadline;

    private String status;
}