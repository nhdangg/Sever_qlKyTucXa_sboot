package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequest_Request {

    @Size(max = 30, message = "STRING_TOO_LONG")
    private String requestCode;

    private String roomId;

    private String reportedBy;

    @Size(max = 50, message = "STRING_TOO_LONG")
    private String category;

    @NotBlank(message = "DESCRIPTION_BLANK")
    @Size(max = 500, message = "STRING_TOO_LONG")
    private String description;

    private String priority;

    private String status;

    private String assignedTo;
    @Size(max = 500, message = "STRING_TOO_LONG")
    private String resolutionNote;
}