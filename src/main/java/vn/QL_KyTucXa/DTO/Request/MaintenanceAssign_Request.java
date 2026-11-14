package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaintenanceAssign_Request {

    @NotBlank(message = "ID_BLANK")
    String staffId;

    String note;
}
