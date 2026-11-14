package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Floor_Request;
import vn.QL_KyTucXa.DTO.Response.Floor_Response;
import vn.QL_KyTucXa.Service.Floor_Service;

import java.util.List;

@RestController
@RequestMapping("/api/floors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Floor_Controller {

    Floor_Service floorService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<Floor_Response> createFloor(@RequestBody @Valid Floor_Request request) {
        return Api_Response.<Floor_Response>builder()
                .result(floorService.create(request))
                .build();
    }

    @GetMapping("/{floorId}")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<Floor_Response> getFloorById(@PathVariable String floorId) {
        return Api_Response.<Floor_Response>builder()
                .result(floorService.getById(floorId))
                .build();
    }

    @GetMapping("/building/{buildingId}")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<List<Floor_Response>> getFloorsByBuilding(@PathVariable String buildingId) {
        return Api_Response.<List<Floor_Response>>builder()
                .result(floorService.getByBuildingId(buildingId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<List<Floor_Response>> getAllFloors() {
        return Api_Response.<List<Floor_Response>>builder()
                .result(floorService.getAll())
                .build();
    }

    @PutMapping("/{floorId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<Floor_Response> updateFloor(
            @PathVariable String floorId,
            @RequestBody @Valid Floor_Request request
    ) {
        return Api_Response.<Floor_Response>builder()
                .result(floorService.update(floorId, request))
                .build();
    }

    @DeleteMapping("/{floorId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<String> deleteFloor(@PathVariable String floorId) {
        floorService.delete(floorId);
        return Api_Response.<String>builder()
                .result(floorId)
                .build();
    }
}
