package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bed_Request {

    @NotBlank(message = "ID_BLANK")
    private String roomId;

    @NotBlank(message = "BED_NUMBER_BLANK")
    @Size(max = 10, message = "STRING_TOO_LONG")
    private String bedNumber;

    private String status;
}