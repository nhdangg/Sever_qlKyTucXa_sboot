package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceGenerate_Request {

    @NotNull(message = "INVOICE_MONTH_BLANK")
    LocalDate invoiceMonth;

    @NotNull(message = "DUE_DATE_BLANK")
    LocalDate dueDate;

    String buildingId;
    String floorId;
}
