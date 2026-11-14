package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset_Request {

    @NotBlank(message = "ASSET_CODE_BLANK")
    @Size(max = 30, message = "STRING_TOO_LONG")
    private String assetCode;

    @NotBlank(message = "ASSET_NAME_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String assetName;

    @Size(max = 50, message = "STRING_TOO_LONG")
    private String category;
    private BigDecimal unitPrice;
    private LocalDate purchaseDate;
    private LocalDate warrantyUntil;

    private String status;
}