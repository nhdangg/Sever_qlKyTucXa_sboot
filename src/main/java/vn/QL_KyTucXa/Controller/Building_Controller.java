package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Building_Request;
import vn.QL_KyTucXa.DTO.Response.Building_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.Building_Service;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Building_Controller {

    Building_Service buildingService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<Building_Response> createBuilding(@RequestBody @Valid Building_Request request) {
        return Api_Response.<Building_Response>builder()
                .result(buildingService.create(request))
                .build();
    }

    @GetMapping("/{buildingId}")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<Building_Response> getBuildingById(@PathVariable String buildingId) {
        return Api_Response.<Building_Response>builder()
                .result(buildingService.getById(buildingId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<Page_Response<Building_Response>> getAllBuildings(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Building_Response>>builder()
                .result(buildingService.getAll(keyword, page, size, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROOM_VIEW')")
    public Api_Response<List<Building_Response>> getAllBuildingsWithoutPaging() {
        return Api_Response.<List<Building_Response>>builder()
                .result(buildingService.getAll())
                .build();
    }

    @PutMapping("/{buildingId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<Building_Response> updateBuilding(
            @PathVariable String buildingId,
            @RequestBody @Valid Building_Request request
    ) {
        return Api_Response.<Building_Response>builder()
                .result(buildingService.update(buildingId, request))
                .build();
    }

    @DeleteMapping("/{buildingId}")
    @PreAuthorize("hasAuthority('ROOM_MANAGE')")
    public Api_Response<String> deleteBuilding(@PathVariable String buildingId) {
        buildingService.delete(buildingId);
        return Api_Response.<String>builder()
                .result(buildingId)
                .build();
    }
}
