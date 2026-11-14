package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Bed_Request;
import vn.QL_KyTucXa.DTO.Response.Bed_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.Bed_Service;

import java.util.List;

@RestController
@RequestMapping("/api/beds")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Bed_Controller {

        Bed_Service bedService;

        @PostMapping
        @PreAuthorize("hasAuthority('ROOM_MANAGE')")
        public Api_Response<Bed_Response> createBed(@RequestBody @Valid Bed_Request request) {
                return Api_Response.<Bed_Response>builder()
                                .result(bedService.create(request))
                                .build();
        }

        @GetMapping("/{bedId}")
        @PreAuthorize("hasAuthority('ROOM_VIEW')")
        public Api_Response<Bed_Response> getBedById(@PathVariable String bedId) {
                return Api_Response.<Bed_Response>builder()
                                .result(bedService.getById(bedId))
                                .build();
        }

        @GetMapping("/room/{roomId}")
        @PreAuthorize("hasAuthority('ROOM_VIEW') or isAuthenticated()")
        public Api_Response<List<Bed_Response>> getBedsByRoom(@PathVariable String roomId) {
                return Api_Response.<List<Bed_Response>>builder()
                                .result(bedService.getByRoomId(roomId))
                                .build();
        }

        @GetMapping("/available/room/{roomId}")
        @PreAuthorize("hasAuthority('ROOM_VIEW') or isAuthenticated()")
        public Api_Response<List<Bed_Response>> getAvailableBedsByRoom(@PathVariable String roomId) {
                return Api_Response.<List<Bed_Response>>builder()
                                .result(bedService.getBedsByRoomId(roomId))
                                .build();
        }

        @GetMapping("/available/building/{buildingId}")
        @PreAuthorize("hasAuthority('ROOM_VIEW')")
        public Api_Response<List<Bed_Response>> getAvailableBedsByBuilding(@PathVariable String buildingId) {
                return Api_Response.<List<Bed_Response>>builder()
                                .result(bedService.getBedsByBuildingId(buildingId))
                                .build();
        }

        @GetMapping
        @PreAuthorize("hasAuthority('ROOM_VIEW')")
        public Api_Response<List<Bed_Response>> getAllBeds() {
                return Api_Response.<List<Bed_Response>>builder()
                                .result(bedService.getAll())
                                .build();
        }

        @GetMapping("/search")
        @PreAuthorize("hasAuthority('ROOM_VIEW') or isAuthenticated()")
        public Api_Response<Page_Response<Bed_Response>> searchBeds(
                        @RequestParam(required = false) String buildingId,
                        @RequestParam(required = false) String roomId,
                        @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "bedNumber") String sortBy,
                        @RequestParam(defaultValue = "asc") String sortDir) {
                return Api_Response.<Page_Response<Bed_Response>>builder()
                                .result(bedService.searchBeds(buildingId, roomId, status, page, size, sortBy, sortDir))
                                .build();
        }

        @PutMapping("/{bedId}")
        @PreAuthorize("hasAuthority('ROOM_MANAGE')")
        public Api_Response<Bed_Response> updateBed(
                        @PathVariable String bedId,
                        @RequestBody @Valid Bed_Request request) {
                return Api_Response.<Bed_Response>builder()
                                .result(bedService.update(bedId, request))
                                .build();
        }

        @PatchMapping("/{bedId}/status")
        @PreAuthorize("hasAuthority('ROOM_STATUS') or hasAuthority('ROOM_MANAGE')")
        public Api_Response<Bed_Response> updateBedStatus(
                        @PathVariable String bedId,
                        @RequestParam String status) {
                return Api_Response.<Bed_Response>builder()
                                .result(bedService.updateStatus(bedId, status))
                                .build();
        }

        @DeleteMapping("/{bedId}")
        @PreAuthorize("hasAuthority('ROOM_MANAGE')")
        public Api_Response<String> deleteBed(@PathVariable String bedId) {
                bedService.delete(bedId);
                return Api_Response.<String>builder()
                                .result(bedId)
                                .build();
        }
}
