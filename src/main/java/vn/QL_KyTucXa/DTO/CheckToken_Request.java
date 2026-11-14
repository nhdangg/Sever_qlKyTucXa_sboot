package vn.QL_KyTucXa.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckToken_Request {
    @com.fasterxml.jackson.annotation.JsonProperty("token")
    @com.fasterxml.jackson.annotation.JsonAlias({ "Token" })
    String token;

    public static class CheckToken_RequestBuilder {
        public CheckToken_RequestBuilder Token(String token) {
            this.token = token;
            return this;
        }
    }
}
