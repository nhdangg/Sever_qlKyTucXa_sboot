package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback_Request {

    private String studentId;

    @Size(max = 50, message = "STRING_TOO_LONG")
    private String category;

    @NotBlank(message = "CONTENT_BLANK")
    private String content;

    @Min(value = 1, message = "RATING_INVALID")
    @Max(value = 5, message = "RATING_INVALID")
    private Integer rating;

    private String status;

    private String response;
    private String respondedBy;
}