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
public class Contract_Response {
    String contractId;
    String contractCode;
    String studentId;
    String studentCode;
    String studentFullName;
    String bedId;
    String bedNumber;
    String roomId;
    String roomNumber;
    String periodId;
    String periodName;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal depositAmount;
    BigDecimal monthlyFee;
    LocalDate signedDate;
    LocalDate terminatedDate;
    String terminationReason;
    String approvedByStaffId;
    String approvedByStaffName;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
