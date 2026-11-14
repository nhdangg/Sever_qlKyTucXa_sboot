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
public class Bed_Response {
    String bedId;
    String roomId;
    String roomNumber;
    String buildingId;
    String buildingName;
    String roomTypeId;
    String roomTypeName;
    BigDecimal pricePerMonth;
    String bedNumber;
    String status;
}