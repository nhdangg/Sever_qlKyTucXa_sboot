package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
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
public class Payment_Request {

    @Size(max = 30, message = "STRING_TOO_LONG")
    private String paymentCode;

    @NotBlank(message = "ID_BLANK")
    private String invoiceId;

    @NotBlank(message = "PAYMENT_METHOD_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String paymentMethod;

    @NotNull(message = "PAYMENT_AMOUNT_INVALID")
    @DecimalMin(value = "1000.0", message = "PAYMENT_AMOUNT_INVALID")
    private BigDecimal amount;

    @Size(max = 100, message = "STRING_TOO_LONG")
    private String transactionRef;
    private String receivedBy;
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String note;

    private String status;
}