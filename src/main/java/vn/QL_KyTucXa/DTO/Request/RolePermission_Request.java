package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission_Request {

    @NotBlank(message = "ROLE_ID_BLANK")
    @Size(max = 36, message = "STRING_TOO_LONG")
    private String roleId;

    @NotBlank(message = "ID_BLANK")
    @Size(max = 36, message = "STRING_TOO_LONG")
    private String permissionId;
}