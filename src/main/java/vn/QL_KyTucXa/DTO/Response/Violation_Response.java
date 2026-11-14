package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Violation_Response {
    String violationId;
    String studentId;
    String studentCode;
    String studentFullName;
    String violationTypeId;
    String violationName;
    LocalDate violationDate;
    String description;
    String actionTaken;
    BigDecimal fineAmount;
    Integer penaltyPointsApplied;
    String handledByStaffId;
    String handledByStaffName;
    String status;
    LocalDateTime createdAt;
}
