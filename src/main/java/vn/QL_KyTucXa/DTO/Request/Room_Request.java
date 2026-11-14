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
public class Room_Request {

    @NotBlank(message = "ID_BLANK")
    private String floorId;

    @NotBlank(message = "ID_BLANK")
    private String roomTypeId;

    @NotBlank(message = "ROOM_NUMBER_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String roomNumber;

    @NotNull(message = "CAPACITY_INVALID")
    @Min(value = 1, message = "CAPACITY_INVALID")
    private Integer capacity;

    private String status;
}