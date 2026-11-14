package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Staff_Response {
    String staffId;
    String userId;
    String username;
    String staffCode;
    String fullName;
    String position;
    String department;
    String phone;
    String email;
    LocalDate hireDate;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
