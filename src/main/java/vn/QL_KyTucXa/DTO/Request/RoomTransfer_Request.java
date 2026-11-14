package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTransfer_Request {

    private String contractId;

    private String oldBedId;

    private String newBedId;

    private String targetRoomId;

    @NotNull(message = "TRANSFER_DATE_BLANK")
    private LocalDate transferDate;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String reason;
    private String approvedBy;

    private String status;
}