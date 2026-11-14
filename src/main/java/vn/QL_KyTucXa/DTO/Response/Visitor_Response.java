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
public class Visitor_Response {
    String visitorId;
    String visitorName;
    String nationalId;
    String phone;
    String hostStudentId;
    String hostStudentCode;
    String hostStudentFullName;
    String roomId;
    String roomNumber;
    String purpose;
    LocalDateTime checkInTime;
    LocalDateTime checkOutTime;
    String idCardImageUrl;
    String approvedByStaffId;
    String approvedByStaffName;
    String status;
}
