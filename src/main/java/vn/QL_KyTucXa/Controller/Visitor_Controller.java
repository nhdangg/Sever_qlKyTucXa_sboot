package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Visitor_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Visitor_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.Visitor_Service;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/visitors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Visitor_Controller {

    Visitor_Service visitorService;
    CurrentUser_Service currentUserService;

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'STUDENT')")
    public Api_Response<Visitor_Response> checkIn(@RequestBody @Valid Visitor_Request request) {
        if (currentUserService.hasRole("STUDENT")) {
            request.setHostStudentId(currentUserService.studentId());
            request.setApprovedBy(null);
            if (request.getCheckInTime() == null) {
                request.setCheckInTime(LocalDateTime.now());
            }
        }
        return Api_Response.<Visitor_Response>builder()
                .result(visitorService.checkIn(request))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<Visitor_Response>> getMyVisitors() {
        return Api_Response.<List<Visitor_Response>>builder()
                .result(visitorService.getByHostStudentId(currentUserService.studentId()))
                .build();
    }

    @PatchMapping("/{visitorId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('VISITOR_MANAGE')")
    public Api_Response<Visitor_Response> approveVisitor(@PathVariable String visitorId) {
        return Api_Response.<Visitor_Response>builder()
                .result(visitorService.approve(visitorId))
                .build();
    }

    @PatchMapping("/{visitorId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('VISITOR_MANAGE')")
    public Api_Response<Visitor_Response> rejectVisitor(
            @PathVariable String visitorId,
            @RequestParam(value = "reason", required = false) String reason
    ) {
        return Api_Response.<Visitor_Response>builder()
                .result(visitorService.reject(visitorId, reason))
                .build();
    }

    @PatchMapping("/{visitorId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Visitor_Response> cancelVisitor(@PathVariable String visitorId) {
        return Api_Response.<Visitor_Response>builder()
                .result(visitorService.cancel(visitorId))
                .build();
    }

    @PutMapping("/{visitorId}/check-out")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('VISITOR_MANAGE') or hasRole('STUDENT')")
    public Api_Response<Visitor_Response> checkOut(@PathVariable String visitorId) {
        return Api_Response.<Visitor_Response>builder()
                .result(visitorService.checkOut(visitorId))
                .build();
    }

    @GetMapping("/{visitorId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Visitor_Response> getVisitorById(@PathVariable String visitorId) {
        return Api_Response.<Visitor_Response>builder()
                .result(visitorService.getById(visitorId))
                .build();
    }

    @GetMapping("/host/{studentId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<Visitor_Response>> getVisitorsByHostStudent(@PathVariable String studentId) {
        return Api_Response.<List<Visitor_Response>>builder()
                .result(visitorService.getByHostStudentId(studentId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'STUDENT')")
    public Api_Response<Page_Response<Visitor_Response>> getAllVisitors(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "hostStudentId", required = false) String hostStudentId,
            @RequestParam(value = "roomId", required = false) String roomId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "currentlyInDorm", required = false) Boolean currentlyInDorm,
            @RequestParam(value = "fromDate", required = false) LocalDateTime fromDate,
            @RequestParam(value = "toDate", required = false) LocalDateTime toDate,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "checkInTime") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Visitor_Response>>builder()
                .result(visitorService.search(keyword, hostStudentId, roomId, status, currentlyInDorm, fromDate, toDate, page, size, sortBy, sortDir))
                .build();
    }

    @DeleteMapping("/{visitorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Api_Response<String> deleteVisitor(@PathVariable String visitorId) {
        visitorService.delete(visitorId);
        return Api_Response.<String>builder()
                .result(visitorId)
                .build();
    }
}
