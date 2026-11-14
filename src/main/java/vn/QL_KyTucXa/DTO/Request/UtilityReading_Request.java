package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilityReading_Request {

    @NotBlank(message = "ID_BLANK")
    private String roomId;

    @NotBlank(message = "ID_BLANK")
    private String serviceTypeId;

    @NotNull(message = "READING_MONTH_BLANK")
    private LocalDate readingMonth;

    @DecimalMin(value = "0.0", message = "INDEX_INVALID")
    private BigDecimal previousIndex;

    @NotNull(message = "INDEX_INVALID")
    @DecimalMin(value = "0.0", message = "INDEX_INVALID")
    private BigDecimal currentIndex;

    private String recordedBy;

    // Cờ đánh dấu thay mới hoặc khởi tạo lại đồng hồ đo (cho phép chỉ số cuối nhỏ hơn hoặc bằng đầu kỳ cũ)
    private Boolean isMeterReset;
}