package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RolePermissionAssign_Request {

    @NotBlank(message = "ROLE_ID_BLANK")
    String roleId;

    @NotEmpty(message = "ROLE_PERMISSION_LIST_EMPTY")
    List<String> permissionIds;
}

