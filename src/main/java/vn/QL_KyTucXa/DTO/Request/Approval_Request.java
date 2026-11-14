package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Approval_Request {

    @NotNull(message = "APPROVED_BLANK")
    Boolean approved;

    String note;
}
