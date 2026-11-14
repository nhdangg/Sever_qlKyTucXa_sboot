package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomTransfer_Response {
    String transferId;
    String contractId;
    String contractCode;
    String studentId;
    String studentFullName;
    String oldBedId;
    String oldBedNumber;
    String oldRoomNumber;
    String newBedId;
    String newBedNumber;
    String newRoomNumber;
    LocalDate transferDate;
    String reason;
    String approvedByStaffId;
    String approvedByStaffName;
    String status;
    LocalDateTime createdAt;
}
