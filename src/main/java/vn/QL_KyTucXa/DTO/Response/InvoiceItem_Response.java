package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvoiceItem_Response {
    String itemId;
    String invoiceId;
    String itemType;
    String description;
    BigDecimal quantity;
    BigDecimal unitPrice;
    BigDecimal amount;
    String referenceId;
}
