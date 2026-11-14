package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification_Request {

    @NotBlank(message = "TITLE_BLANK")
    @Size(max = 255, message = "STRING_TOO_LONG")
    private String title;

    @NotBlank(message = "CONTENT_BLANK")
    private String content;

    private String targetType;

    @Size(max = 36, message = "STRING_TOO_LONG")
    private String targetId;

    private String createdBy;

    private Boolean isPublished;
}