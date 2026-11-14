package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class RoomType_Request {

    @NotBlank(message = "ROOM_TYPE_NAME_BLANK")
    @Size(max = 50, message = "STRING_TOO_LONG")
    private String typeName;

    @NotNull(message = "CAPACITY_INVALID")
    @Min(value = 1, message = "CAPACITY_INVALID")
    private Integer capacity;

    private BigDecimal areaM2;

    @NotNull(message = "PRICE_INVALID")
    @DecimalMin(value = "0.0", inclusive = false, message = "PRICE_INVALID")
    private BigDecimal pricePerMonth;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}