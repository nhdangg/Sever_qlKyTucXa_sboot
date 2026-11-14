package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Building_Response {
    String buildingId;
    String buildingCode;
    String buildingName;
    String address;
    Integer numFloors;
    String genderType;
    String description;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}