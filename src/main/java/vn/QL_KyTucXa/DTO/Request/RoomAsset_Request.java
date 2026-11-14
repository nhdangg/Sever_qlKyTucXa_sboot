package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAsset_Request {

    @NotBlank(message = "ID_BLANK")
    private String roomId;

    @NotBlank(message = "ID_BLANK")
    private String assetId;

    @NotNull(message = "QUANTITY_INVALID")
    @Min(value = 1, message = "QUANTITY_INVALID")
    private Integer quantity;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String conditionNote;

    @NotNull(message = "ASSIGNED_DATE_BLANK")
    private LocalDate assignedDate;
}