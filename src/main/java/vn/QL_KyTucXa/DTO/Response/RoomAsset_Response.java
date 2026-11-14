package vn.QL_KyTucXa.DTO.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomAsset_Response {
    String roomAssetId;
    String roomId;
    String roomNumber;
    String assetId;
    String assetCode;
    String assetName;
    Integer quantity;
    String conditionNote;
    LocalDate assignedDate;
}
