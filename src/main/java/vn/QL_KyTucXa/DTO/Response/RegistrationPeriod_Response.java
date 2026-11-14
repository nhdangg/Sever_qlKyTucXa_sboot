package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistrationPeriod_Response {
    String periodId;
    String periodName;
    String academicYear;
    LocalDate startDate;
    LocalDate endDate;
    LocalDate registrationDeadline;
    String status;
    LocalDateTime createdAt;
}
