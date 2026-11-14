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
public class Feedback_Response {
    String feedbackId;
    String studentId;
    String studentCode;
    String studentFullName;
    String category;
    String content;
    Integer rating;
    String status;
    String response;
    String respondedByStaffId;
    String respondedByStaffName;
    LocalDateTime respondedAt;
    LocalDateTime createdAt;
}
