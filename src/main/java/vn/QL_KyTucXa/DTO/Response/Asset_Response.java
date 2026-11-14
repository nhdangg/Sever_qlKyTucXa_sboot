package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Asset_Response {
    String assetId;
    String assetCode;
    String assetName;
    String category;
    BigDecimal unitPrice;
    LocalDate purchaseDate;
    LocalDate warrantyUntil;
    String status;
    LocalDateTime createdAt;
}
