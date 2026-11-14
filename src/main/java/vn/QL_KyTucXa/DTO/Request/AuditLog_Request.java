package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog_Request {

    private String userId;

    @NotBlank(message = "ACTION_BLANK")
    @Size(max = 50, message = "STRING_TOO_LONG")
    private String action;

    @Size(max = 100, message = "STRING_TOO_LONG")
    private String tableName;
    @Size(max = 36, message = "STRING_TOO_LONG")
    private String recordId;
    private String oldValue;
    private String newValue;
    @Size(max = 45, message = "STRING_TOO_LONG")
    private String ipAddress;
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String userAgent;
}