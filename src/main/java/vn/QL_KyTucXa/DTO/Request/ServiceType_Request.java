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
public class ServiceType_Request {

    @NotBlank(message = "SERVICE_CODE_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String serviceCode;

    @NotBlank(message = "SERVICE_NAME_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String serviceName;

    @Size(max = 20, message = "STRING_TOO_LONG")
    private String unit;

    @NotNull(message = "PRICE_INVALID")
    @DecimalMin(value = "0.0", message = "PRICE_INVALID")
    private BigDecimal unitPrice;

    private String billingType;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}