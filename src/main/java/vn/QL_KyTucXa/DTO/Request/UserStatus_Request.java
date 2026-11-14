package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserStatus_Request {

    @NotBlank(message = "STATUS_BLANK")
    @Pattern(regexp = "^(ACTIVE|LOCKED)$", message = "STATUS_INVALID")
    String status;
}

