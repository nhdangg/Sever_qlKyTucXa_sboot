package vn.QL_KyTucXa.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleRegistration_Request {

    private String studentId;

    @NotBlank(message = "VEHICLE_TYPE_BLANK")
    @Size(max = 20, message = "STRING_TOO_LONG")
    private String vehicleType;

    @Size(max = 20, message = "STRING_TOO_LONG")
    private String licensePlate;
    @Size(max = 100, message = "STRING_TOO_LONG")
    private String brandModel;

    private BigDecimal monthlyFee;

    private LocalDate registrationDate;

    private LocalDate expiryDate;

    private String status;
}