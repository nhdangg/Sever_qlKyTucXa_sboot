package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleRegistration_Response {
    String vehicleId;
    String studentId;
    String studentCode;
    String studentFullName;
    String vehicleType;
    String licensePlate;
    String brandModel;
    BigDecimal monthlyFee;
    LocalDate registrationDate;
    LocalDate expiryDate;
    String status;
    LocalDateTime createdAt;
}
