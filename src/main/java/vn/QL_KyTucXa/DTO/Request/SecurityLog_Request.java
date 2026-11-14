package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityLog_Request {

    @NotBlank(message = "EVENT_TYPE_BLANK")
    @Size(max = 30, message = "STRING_TOO_LONG")
    private String eventType;

    private String studentId;
    private String roomId;
    @Size(max = 500, message = "STRING_TOO_LONG")
    private String description;
    private String recordedBy;
//    private LocalDateTime eventTime;
}
