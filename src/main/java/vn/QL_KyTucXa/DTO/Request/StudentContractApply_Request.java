package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentContractApply_Request {

    @NotBlank(message = "ID_BLANK")
    private String bedId;

    private String periodId;

    private String notes;
}
