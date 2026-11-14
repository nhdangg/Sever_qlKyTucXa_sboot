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
public class Room_Response {
    String roomId;
    String floorId;
    Integer floorNumber;
    String buildingId;
    String buildingName;
    String roomTypeId;
    String roomTypeName;
    String roomNumber;
    Integer capacity;
    Integer currentOccupancy;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}