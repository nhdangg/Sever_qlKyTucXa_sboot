package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Payment_Response {
    String paymentId;
    String paymentCode;
    String invoiceId;
    String invoiceCode;
    String paymentMethod;
    BigDecimal amount;
    String transactionRef;
    LocalDateTime paymentDate;
    String receivedByStaffId;
    String receivedByStaffName;
    String status;
    String note;
}
