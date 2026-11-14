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
public class InvoiceItem_Request {

    private String invoiceId;

    @NotBlank(message = "ITEM_TYPE_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String itemType;

    @NotBlank(message = "DESCRIPTION_BLANK")
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;

    @NotNull(message = "QUANTITY_INVALID")
    @DecimalMin(value = "0.01", message = "QUANTITY_INVALID")
    private BigDecimal quantity;

    @NotNull(message = "PRICE_INVALID")
    @DecimalMin(value = "0.0", message = "PRICE_INVALID")
    private BigDecimal unitPrice;

    private BigDecimal amount;

    @Size(max = 36, message = "STRING_TOO_LONG")
    private String referenceId;
}