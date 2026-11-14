package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission_Request {

    @NotBlank(message = "PERMISSION_CODE_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String permissionCode;

    @NotBlank(message = "MODULE_BLANK")
    @Size(max = 50, message = "STRING_TOO_LONG")
    private String module;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}