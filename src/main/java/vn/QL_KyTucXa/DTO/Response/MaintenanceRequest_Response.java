package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MaintenanceRequest_Response {
    String requestId;
    String requestCode;
    String roomId;
    String roomNumber;
    String reportedByUserId;
    String reportedByUsername;
    String category;
    String description;
    String priority;
    String status;
    String assignedToStaffId;
    String assignedToStaffName;
    LocalDateTime requestedDate;
    LocalDateTime completedDate;
    String resolutionNote;
}
