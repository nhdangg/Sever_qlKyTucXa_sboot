package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.UtilityReading_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.DTO.Response.UtilityReading_Response;
import vn.QL_KyTucXa.DTO.Response.UtilitySplit_Response;
import vn.QL_KyTucXa.Service.Invoice_Service;
import vn.QL_KyTucXa.Service.UtilityReading_Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/utility-readings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UtilityReading_Controller {

    UtilityReading_Service readingService;
    Invoice_Service invoiceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_MANAGE')")
    public Api_Response<UtilityReading_Response> recordReading(@RequestBody @Valid UtilityReading_Request request) {
        UtilityReading_Response saved = readingService.create(request);
        if (saved != null && saved.getRoomId() != null && saved.getReadingMonth() != null) {
            invoiceService.generateOrUpdateInvoicesForRoomReading(saved.getRoomId(), saved.getReadingMonth());
        }
        return Api_Response.<UtilityReading_Response>builder()
                .result(saved)
                .build();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_MANAGE')")
    public Api_Response<List<UtilityReading_Response>> recordBatch(@RequestBody @Valid List<UtilityReading_Request> requests) {
        List<UtilityReading_Response> savedList = readingService.createBatch(requests);
        if (savedList != null) {
            Set<String> syncedRoomMonths = new HashSet<>();
            for (UtilityReading_Response r : savedList) {
                if (r != null && r.getRoomId() != null && r.getReadingMonth() != null) {
                    String key = r.getRoomId() + "_" + r.getReadingMonth().withDayOfMonth(1);
                    if (syncedRoomMonths.add(key)) {
                        invoiceService.generateOrUpdateInvoicesForRoomReading(r.getRoomId(), r.getReadingMonth());
                    }
                }
            }
        }
        return Api_Response.<List<UtilityReading_Response>>builder()
                .result(savedList)
                .build();
    }

    @GetMapping("/unrecorded-rooms")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_VIEW')")
    public Api_Response<List<Room_Response>> getUnrecordedRooms(
            @RequestParam(value = "readingMonth", required = false) LocalDate readingMonth,
            @RequestParam(value = "serviceTypeId", required = false) String serviceTypeId,
            @RequestParam(value = "buildingId", required = false) String buildingId
    ) {
        return Api_Response.<List<Room_Response>>builder()
                .result(readingService.getUnrecordedRooms(readingMonth, serviceTypeId, buildingId))
                .build();
    }

    @GetMapping("/split/room/{roomId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<UtilitySplit_Response> getUtilitySplit(
            @PathVariable String roomId,
            @RequestParam String serviceTypeId,
            @RequestParam(required = false) LocalDate readingMonth
    ) {
        return Api_Response.<UtilitySplit_Response>builder()
                .result(readingService.calculateRoomUtilitySplit(roomId, serviceTypeId, readingMonth))
                .build();
    }

    @GetMapping("/{readingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_VIEW')")
    public Api_Response<UtilityReading_Response> getReadingById(@PathVariable String readingId) {
        return Api_Response.<UtilityReading_Response>builder()
                .result(readingService.getById(readingId))
                .build();
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<UtilityReading_Response>> getReadingsByRoom(@PathVariable String roomId) {
        return Api_Response.<List<UtilityReading_Response>>builder()
                .result(readingService.getByRoomId(roomId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_VIEW')")
    public Api_Response<Page_Response<UtilityReading_Response>> getAllReadings(
            @RequestParam(value = "buildingId", required = false) String buildingId,
            @RequestParam(value = "roomId", required = false) String roomId,
            @RequestParam(value = "serviceTypeId", required = false) String serviceTypeId,
            @RequestParam(value = "readingMonth", required = false) LocalDate readingMonth,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "readingMonth") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<UtilityReading_Response>>builder()
                .result(readingService.search(buildingId, roomId, serviceTypeId, readingMonth, page, size, sortBy, sortDir))
                .build();
    }

    @PutMapping("/{readingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_MANAGE')")
    public Api_Response<UtilityReading_Response> updateReading(
            @PathVariable String readingId,
            @RequestBody @Valid UtilityReading_Request request
    ) {
        UtilityReading_Response updated = readingService.update(readingId, request);
        if (updated != null && updated.getRoomId() != null && updated.getReadingMonth() != null) {
            invoiceService.generateOrUpdateInvoicesForRoomReading(updated.getRoomId(), updated.getReadingMonth());
        }
        return Api_Response.<UtilityReading_Response>builder()
                .result(updated)
                .build();
    }

    @DeleteMapping("/{readingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Api_Response<String> deleteReading(@PathVariable String readingId) {
        readingService.delete(readingId);
        return Api_Response.<String>builder()
                .result(readingId)
                .build();
    }
}
