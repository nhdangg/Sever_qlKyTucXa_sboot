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
public class AuditLog_Response {
    String logId;
    String userId;
    String username;
    String action;
    String tableName;
    String recordId;
    String oldValue;
    String newValue;
    String ipAddress;
    String userAgent;
    LocalDateTime createdAt;
}