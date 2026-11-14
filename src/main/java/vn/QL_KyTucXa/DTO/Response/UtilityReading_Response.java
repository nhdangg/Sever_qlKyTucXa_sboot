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
public class UtilityReading_Response {
    String readingId;
    String roomId;
    String roomNumber;
    String serviceTypeId;
    String serviceName;
    LocalDate readingMonth;
    BigDecimal previousIndex;
    BigDecimal currentIndex;
    BigDecimal consumption;
    String recordedByStaffId;
    String recordedByStaffName;
    LocalDateTime recordedAt;
}
