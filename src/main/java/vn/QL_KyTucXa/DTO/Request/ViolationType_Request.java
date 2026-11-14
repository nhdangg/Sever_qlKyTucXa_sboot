package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationType_Request {

    @NotBlank(message = "VIOLATION_NAME_BLANK")
    @Size(max = 150, message = "STRING_TOO_LONG")
    private String violationName;

    private String severityLevel;

    @NotNull(message = "PENALTY_POINTS_INVALID")
    @Min(value = 0, message = "PENALTY_POINTS_INVALID")
    private Integer penaltyPoints;

    @DecimalMin(value = "0", message = "PRICE_INVALID")
    private BigDecimal fineAmount;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}