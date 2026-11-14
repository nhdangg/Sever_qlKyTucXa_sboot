package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting_Request {

    @NotBlank(message = "SETTING_KEY_BLANK")
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String settingKey;

    @NotBlank(message = "SETTING_VALUE_BLANK")
    @Size(max = 500, message = "STRING_TOO_LONG")
    private String settingValue;

    @Size(max = 255, message = "STRING_TOO_LONG")
    private String description;
}