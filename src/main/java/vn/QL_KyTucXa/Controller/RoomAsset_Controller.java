package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.RoomAsset_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomAsset_Response;
import vn.QL_KyTucXa.Service.RoomAsset_Service;

import java.util.List;

@RestController
@RequestMapping("/api/room-assets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomAsset_Controller {

    RoomAsset_Service roomAssetService;

    @PostMapping
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public Api_Response<RoomAsset_Response> assignAssetToRoom(@RequestBody @Valid RoomAsset_Request request) {
        return Api_Response.<RoomAsset_Response>builder()
                .result(roomAssetService.create(request))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ASSET_VIEW') or isAuthenticated()")
    public Api_Response<Page_Response<RoomAsset_Response>> getAllRoomAssets(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "buildingId", required = false) String buildingId,
            @RequestParam(value = "roomId", required = false) String roomId,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "sortBy", defaultValue = "assignedDate") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<RoomAsset_Response>>builder()
                .result(roomAssetService.getAll(page, size, buildingId, roomId, assetId, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ASSET_VIEW') or isAuthenticated()")
    public Api_Response<List<RoomAsset_Response>> getAllRoomAssetsWithoutPaging() {
        return Api_Response.<List<RoomAsset_Response>>builder()
                .result(roomAssetService.getAll())
                .build();
    }

    @GetMapping("/{roomAssetId}")
    @PreAuthorize("hasAuthority('ASSET_VIEW') or isAuthenticated()")
    public Api_Response<RoomAsset_Response> getRoomAssetById(@PathVariable String roomAssetId) {
        return Api_Response.<RoomAsset_Response>builder()
                .result(roomAssetService.getById(roomAssetId))
                .build();
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<RoomAsset_Response>> getAssetsByRoom(@PathVariable String roomId) {
        return Api_Response.<List<RoomAsset_Response>>builder()
                .result(roomAssetService.getByRoomId(roomId))
                .build();
    }

    @GetMapping("/asset/{assetId}")
    @PreAuthorize("hasAuthority('ASSET_VIEW') or isAuthenticated()")
    public Api_Response<List<RoomAsset_Response>> getAssetsByAssetId(@PathVariable String assetId) {
        return Api_Response.<List<RoomAsset_Response>>builder()
                .result(roomAssetService.getByAssetId(assetId))
                .build();
    }

    @PutMapping("/{roomAssetId}")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public Api_Response<RoomAsset_Response> updateRoomAsset(
            @PathVariable String roomAssetId,
            @RequestBody @Valid RoomAsset_Request request
    ) {
        return Api_Response.<RoomAsset_Response>builder()
                .result(roomAssetService.update(roomAssetId, request))
                .build();
    }

    @DeleteMapping("/{roomAssetId}")
    @PreAuthorize("hasAuthority('ASSET_MANAGE')")
    public Api_Response<String> deleteRoomAsset(@PathVariable String roomAssetId) {
        roomAssetService.delete(roomAssetId);
        return Api_Response.<String>builder()
                .result(roomAssetId)
                .build();
    }
}
