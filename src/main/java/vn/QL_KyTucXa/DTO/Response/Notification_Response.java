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
public class Notification_Response {
    String notificationId;
    String title;
    String content;
    String targetType;
    String targetId;
    String createdByUserId;
    String createdByUsername;
    Boolean isPublished;
    LocalDateTime publishedAt;
    LocalDateTime createdAt;
}
