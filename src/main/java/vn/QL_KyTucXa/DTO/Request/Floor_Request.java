package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Floor_Request {

    @NotBlank(message = "ID_BLANK")
    private String buildingId;

    @NotNull(message = "FLOOR_NUMBER_BLANK")
    private Integer floorNumber;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}