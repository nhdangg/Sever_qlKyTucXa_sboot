package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract_Request {

    @NotBlank(message = "CONTRACT_CODE_BLANK")
    @Size(max = 30, message = "CONTRACT_CODE_TOO_LONG")
    private String contractCode;

    @NotBlank(message = "ID_BLANK")
    private String studentId;

    @NotBlank(message = "ID_BLANK")
    private String bedId;

    @NotBlank(message = "ID_BLANK")
    private String periodId;

    @NotNull(message = "START_DATE_BLANK")
    private LocalDate startDate;

    @NotNull(message = "END_DATE_BLANK")
    private LocalDate endDate;

    private BigDecimal depositAmount;

    private BigDecimal monthlyFee;

    private LocalDate signedDate;
    private LocalDate terminatedDate;
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String terminationReason;
    private String approvedBy;

    private String status;
}