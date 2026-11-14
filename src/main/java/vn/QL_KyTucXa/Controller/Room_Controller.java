package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Room_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.Service.Room_Service;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Room_Controller {

    Room_Service roomService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<Room_Response> createRoom(@RequestBody @Valid Room_Request request) {
        return Api_Response.<Room_Response>builder()
                .result(roomService.create(request))
                .build();
    }

    @GetMapping("/{roomId}")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<Room_Response> getRoomById(@PathVariable String roomId) {
        return Api_Response.<Room_Response>builder()
                .result(roomService.getById(roomId))
                .build();
    }

    @GetMapping("/floor/{floorId}")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<List<Room_Response>> getRoomsByFloor(@PathVariable String floorId) {
        return Api_Response.<List<Room_Response>>builder()
                .result(roomService.getByFloorId(floorId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<Page_Response<Room_Response>> getAllRooms(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam(value = "buildingId", required = false) String buildingId,
            @RequestParam(value = "floorId", required = false) String floorId,
            @RequestParam(value = "roomTypeId", required = false) String roomTypeId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hasAvailableBeds", required = false) Boolean hasAvailableBeds,
            @RequestParam(value = "sortBy", defaultValue = "roomNumber") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        Page_Response<Room_Response> result;
        if (roomNumber == null && buildingId == null && floorId == null && roomTypeId == null && status == null && hasAvailableBeds == null) {
            result = roomService.getAll(page, size, sortBy, sortDir);
        } else {
            result = roomService.getAll(page, size, roomNumber, buildingId, floorId, roomTypeId, status, hasAvailableBeds, sortBy, sortDir);
        }
        return Api_Response.<Page_Response<Room_Response>>builder()
                .result(result)
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<List<Room_Response>> getAllRoomsWithoutPaging() {
        return Api_Response.<List<Room_Response>>builder()
                .result(roomService.getAll())
                .build();
    }

    @PutMapping("/{roomId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<Room_Response> updateRoom(
            @PathVariable String roomId,
            @RequestBody @Valid Room_Request request
    ) {
        return Api_Response.<Room_Response>builder()
                .result(roomService.update(roomId, request))
                .build();
    }

    @PatchMapping("/{roomId}/status")
    @PreAuthorize("hasAuthority('ROOM_STATUS')")
    public Api_Response<Room_Response> updateRoomStatus(
            @PathVariable String roomId,
            @RequestParam String status
    ) {
        return Api_Response.<Room_Response>builder()
                .result(roomService.updateStatus(roomId, status))
                .build();
    }

    @DeleteMapping("/{roomId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<String> deleteRoom(@PathVariable String roomId) {
        roomService.delete(roomId);
        return Api_Response.<String>builder()
                .result(roomId)
                .build();
    }
}
