package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRecipient_Request {

    @NotBlank(message = "ID_BLANK")
    private String notificationId;

    @NotBlank(message = "ID_BLANK")
    private String userId;

    private Boolean isRead;
}