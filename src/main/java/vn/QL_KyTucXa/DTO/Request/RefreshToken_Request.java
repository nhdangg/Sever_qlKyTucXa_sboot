package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken_Request {

    @NotBlank(message = "TOKEN_BLANK")
    @Size(max = 512, message = "STRING_TOO_LONG")
    private String token;
}