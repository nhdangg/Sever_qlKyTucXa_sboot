package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.MaintenanceRequest_Request;
import vn.QL_KyTucXa.DTO.Response.MaintenanceRequest_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.MaintenanceRequest_Service;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance-requests")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceRequest_Controller {

    MaintenanceRequest_Service maintenanceService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_CREATE', 'MAINTENANCE_MANAGE')")
    public Api_Response<MaintenanceRequest_Response> createRequest(@RequestBody @Valid MaintenanceRequest_Request request) {
        if (currentUserService.hasRole("STUDENT")) {
            request.setReportedBy(currentUserService.userId());
            request.setStatus("PENDING");
            request.setAssignedTo(null);
            request.setResolutionNote(null);
        } else if (request.getReportedBy() == null || request.getReportedBy().isBlank() || "self".equalsIgnoreCase(request.getReportedBy())) {
            request.setReportedBy(currentUserService.userId());
        }
        return Api_Response.<MaintenanceRequest_Response>builder()
                .result(maintenanceService.create(request))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<MaintenanceRequest_Response>> getMyRequests() {
        return Api_Response.<List<MaintenanceRequest_Response>>builder()
                .result(maintenanceService.getByReporterUserId(currentUserService.userId()))
                .build();
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public Api_Response<MaintenanceRequest_Response> getRequestById(@PathVariable String requestId) {
        return Api_Response.<MaintenanceRequest_Response>builder()
                .result(maintenanceService.getById(requestId))
                .build();
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public Api_Response<List<MaintenanceRequest_Response>> getRequestsByRoom(@PathVariable String roomId) {
        return Api_Response.<List<MaintenanceRequest_Response>>builder()
                .result(maintenanceService.getByRoomId(roomId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public Api_Response<Page_Response<MaintenanceRequest_Response>> getAllRequests(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortBy", defaultValue = "requestedDate") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<MaintenanceRequest_Response>>builder()
                .result(maintenanceService.getAll(page, size, keyword, status, priority, roomNumber, category, sortBy, sortDir))
                .build();
    }

    @PutMapping("/{requestId}/assign")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public Api_Response<MaintenanceRequest_Response> assignStaff(
            @PathVariable String requestId,
            @RequestParam String staffId
    ) {
        return Api_Response.<MaintenanceRequest_Response>builder()
                .result(maintenanceService.assignStaff(requestId, staffId))
                .build();
    }

    @PutMapping("/{requestId}/complete")
    @PreAuthorize("hasAuthority('MAINTENANCE_MANAGE')")
    public Api_Response<MaintenanceRequest_Response> completeRequest(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "Đã hoàn thành sửa chữa") String resolutionNote
    ) {
        return Api_Response.<MaintenanceRequest_Response>builder()
                .result(maintenanceService.complete(requestId, resolutionNote))
                .build();
    }

    @PutMapping("/{requestId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<MaintenanceRequest_Response> cancelRequest(
            @PathVariable String requestId,
            @RequestParam(required = false, defaultValue = "Người dùng đã hủy yêu cầu") String reason
    ) {
        return Api_Response.<MaintenanceRequest_Response>builder()
                .result(maintenanceService.cancel(requestId, reason))
                .build();
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Api_Response<String> deleteRequest(@PathVariable String requestId) {
        maintenanceService.delete(requestId);
        return Api_Response.<String>builder()
                .result(requestId)
                .build();
    }
}
