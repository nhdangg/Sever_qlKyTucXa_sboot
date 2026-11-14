package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation_Request {

    @NotBlank(message = "ID_BLANK")
    private String studentId;

    @NotBlank(message = "ID_BLANK")
    private String violationTypeId;

    @NotNull(message = "VIOLATION_DATE_BLANK")
    private LocalDate violationDate;

    @Size(max = 500, message = "STRING_TOO_LONG")
    private String description;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String actionTaken;

    private BigDecimal fineAmount;

    private Integer penaltyPointsApplied;

    private String handledBy;

    private String status;
}