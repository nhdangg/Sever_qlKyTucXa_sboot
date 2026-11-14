package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building_Request {

    @NotBlank(message = "BUILDING_CODE_BLANK")
    @Size(max = 20, message = "BUILDING_CODE_TOO_LONG")
    private String buildingCode;

    @NotBlank(message = "BUILDING_NAME_BLANK")
    @Size(max = 100, message = "BUILDING_NAME_TOO_LONG")
    private String buildingName;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String address;

    @NotNull(message = "NUM_FLOORS_INVALID")
    @Min(value = 1, message = "NUM_FLOORS_INVALID")
    private Integer numFloors;

    @NotBlank(message = "GENDER_TYPE_BLANK")
    private String genderType;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;

    private String status;
}