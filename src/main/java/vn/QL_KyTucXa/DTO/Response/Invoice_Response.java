package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Invoice_Response {
    String invoiceId;
    String invoiceCode;
    String contractId;
    String contractCode;
    String studentId;
    String studentCode;
    String studentFullName;
    LocalDate invoiceMonth;
    LocalDate issueDate;
    LocalDate dueDate;
    BigDecimal subtotalAmount;
    BigDecimal discountAmount;
    BigDecimal lateFeeAmount;
    BigDecimal totalAmount;
    BigDecimal paidAmount;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    List<InvoiceItem_Response> invoiceItems;
}
