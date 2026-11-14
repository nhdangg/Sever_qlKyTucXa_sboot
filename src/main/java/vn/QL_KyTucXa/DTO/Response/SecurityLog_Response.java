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
public class SecurityLog_Response {
    String logId;
    String eventType;
    String studentId;
    String studentCode;
    String studentFullName;
    String roomId;
    String roomNumber;
    String description;
    String recordedByStaffId;
    String recordedByStaffName;
    LocalDateTime eventTime;
}
