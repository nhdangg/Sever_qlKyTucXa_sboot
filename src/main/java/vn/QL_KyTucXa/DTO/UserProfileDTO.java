package vn.QL_KyTucXa.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String fullName;
    private String userCode;
    private String roleCode;
    private String roleName;
}
