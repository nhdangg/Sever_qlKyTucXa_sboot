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
public class NotificationRecipient_Response {
    String recipientId;
    String notificationId;
    String notificationTitle;
    String title;
    String notificationContent;
    String content;
    String targetType;
    String targetId;
    LocalDateTime createdAt;
    LocalDateTime publishedAt;
    String userId;
    String username;
    Boolean isRead;
    LocalDateTime readAt;
}
