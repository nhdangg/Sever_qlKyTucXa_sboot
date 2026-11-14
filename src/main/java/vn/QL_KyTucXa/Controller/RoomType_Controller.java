package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.RoomType_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomType_Response;
import vn.QL_KyTucXa.Service.RoomType_Service;

import java.util.List;

@RestController
@RequestMapping("/api/room-types")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomType_Controller {

    RoomType_Service roomTypeService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<RoomType_Response> createRoomType(@RequestBody @Valid RoomType_Request request) {
        return Api_Response.<RoomType_Response>builder()
                .result(roomTypeService.create(request))
                .build();
    }

    @GetMapping("/{roomTypeId}")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<RoomType_Response> getRoomTypeById(@PathVariable String roomTypeId) {
        return Api_Response.<RoomType_Response>builder()
                .result(roomTypeService.getById(roomTypeId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<Page_Response<RoomType_Response>> getAllRoomTypes(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "typeName") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        return Api_Response.<Page_Response<RoomType_Response>>builder()
                .result(roomTypeService.getAll(page, size, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<List<RoomType_Response>> getAllRoomTypesWithoutPaging() {
        return Api_Response.<List<RoomType_Response>>builder()
                .result(roomTypeService.getAll())
                .build();
    }

    @PutMapping("/{roomTypeId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<RoomType_Response> updateRoomType(
            @PathVariable String roomTypeId,
            @RequestBody @Valid RoomType_Request request
    ) {
        return Api_Response.<RoomType_Response>builder()
                .result(roomTypeService.update(roomTypeId, request))
                .build();
    }

    @DeleteMapping("/{roomTypeId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<String> deleteRoomType(@PathVariable String roomTypeId) {
        roomTypeService.delete(roomTypeId);
        return Api_Response.<String>builder()
                .result(roomTypeId)
                .build();
    }
}
