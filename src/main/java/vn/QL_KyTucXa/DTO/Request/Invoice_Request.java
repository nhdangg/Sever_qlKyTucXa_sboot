package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice_Request {

    @Size(max = 30, message = "STRING_TOO_LONG")
    private String invoiceCode;

    @NotBlank(message = "ID_BLANK")
    private String contractId;

    private String studentId;

    private LocalDate invoiceMonth;

    private LocalDate issueDate;

    @NotNull(message = "DUE_DATE_BLANK")
    private LocalDate dueDate;

    private BigDecimal subtotalAmount;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal lateFeeAmount;

    private String status;

    private List<InvoiceItem_Request> invoiceItems;
}