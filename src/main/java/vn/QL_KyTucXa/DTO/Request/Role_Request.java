package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role_Request {

    @NotBlank(message = "ROLE_CODE_BLANK")
    @Size(max = 50, message = "STRING_TOO_LONG")
    private String roleCode;

    @NotBlank(message = "ROLE_NAME_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String roleName;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}