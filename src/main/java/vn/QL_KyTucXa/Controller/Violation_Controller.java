package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Violation_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.StudentViolationSummary_Response;
import vn.QL_KyTucXa.DTO.Response.Violation_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.Violation_Service;

import java.util.List;

@RestController
@RequestMapping("/api/violations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Violation_Controller {

    Violation_Service violationService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('VIOLATION_MANAGE')")
    public Api_Response<Violation_Response> recordViolation(@RequestBody @Valid Violation_Request request) {
        return Api_Response.<Violation_Response>builder()
                .result(violationService.create(request))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<Violation_Response>> getMyViolations() {
        return Api_Response.<List<Violation_Response>>builder()
                .result(violationService.getByStudentId(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/{violationId}")
    @PreAuthorize("hasAuthority('VIOLATION_VIEW')")
    public Api_Response<Violation_Response> getViolationById(@PathVariable String violationId) {
        return Api_Response.<Violation_Response>builder()
                .result(violationService.getById(violationId))
                .build();
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAuthority('VIOLATION_VIEW')")
    public Api_Response<List<Violation_Response>> getViolationsByStudent(@PathVariable String studentId) {
        return Api_Response.<List<Violation_Response>>builder()
                .result(violationService.getByStudentId(studentId))
                .build();
    }

    @GetMapping("/summary/my")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<StudentViolationSummary_Response> getMyViolationSummary() {
        return Api_Response.<StudentViolationSummary_Response>builder()
                .result(violationService.getStudentViolationSummary(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/summary/{studentId}")
    @PreAuthorize("hasAuthority('VIOLATION_VIEW') or hasRole('STUDENT')")
    public Api_Response<StudentViolationSummary_Response> getStudentViolationSummary(@PathVariable String studentId) {
        return Api_Response.<StudentViolationSummary_Response>builder()
                .result(violationService.getStudentViolationSummary(studentId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIOLATION_VIEW')")
    public Api_Response<Page_Response<Violation_Response>> getAllViolations(
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "violationTypeId", required = false) String violationTypeId,
            @RequestParam(value = "severityLevel", required = false) String severityLevel,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "fromDate", required = false) java.time.LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) java.time.LocalDate toDate,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "violationDate") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Violation_Response>>builder()
                .result(violationService.search(studentId, keyword, violationTypeId, severityLevel, status, fromDate, toDate, page, size, sortBy, sortDir))
                .build();
    }

    @PatchMapping("/{violationId}/paid")
    @PreAuthorize("hasAuthority('VIOLATION_MANAGE')")
    public Api_Response<Violation_Response> markViolationAsPaid(@PathVariable String violationId) {
        return Api_Response.<Violation_Response>builder()
                .result(violationService.markAsPaid(violationId))
                .build();
    }

    @PutMapping("/{violationId}")
    @PreAuthorize("hasAuthority('VIOLATION_MANAGE')")
    public Api_Response<Violation_Response> updateViolation(
            @PathVariable String violationId,
            @RequestBody @Valid Violation_Request request
    ) {
        return Api_Response.<Violation_Response>builder()
                .result(violationService.update(violationId, request))
                .build();
    }

    @DeleteMapping("/{violationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Api_Response<String> deleteViolation(@PathVariable String violationId) {
        violationService.delete(violationId);
        return Api_Response.<String>builder()
                .result(violationId)
                .build();
    }
}
