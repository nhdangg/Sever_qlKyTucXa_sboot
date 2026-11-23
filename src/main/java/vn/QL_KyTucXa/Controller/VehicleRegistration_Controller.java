package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.VehicleRegistration_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.VehicleRegistration_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.VehicleRegistration_Service;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vehicle-registrations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VehicleRegistration_Controller {

    VehicleRegistration_Service vehicleService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'STUDENT')")
    public Api_Response<VehicleRegistration_Response> registerVehicle(@RequestBody @Valid VehicleRegistration_Request request) {
        return Api_Response.<VehicleRegistration_Response>builder()
                .result(vehicleService.create(request))
                .build();
    }

    @PatchMapping("/{vehicleId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public Api_Response<VehicleRegistration_Response> approveVehicle(
            @PathVariable String vehicleId,
            @RequestParam(defaultValue = "true") boolean approved
    ) {
        return Api_Response.<VehicleRegistration_Response>builder()
                .result(vehicleService.approve(vehicleId, approved))
                .build();
    }

    @PatchMapping("/{vehicleId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public Api_Response<VehicleRegistration_Response> updateStatus(
            @PathVariable String vehicleId,
            @RequestParam String status
    ) {
        return Api_Response.<VehicleRegistration_Response>builder()
                .result(vehicleService.updateStatus(vehicleId, status))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<VehicleRegistration_Response>> getMyVehicles() {
        return Api_Response.<List<VehicleRegistration_Response>>builder()
                .result(vehicleService.getByStudentId(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/{vehicleId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<VehicleRegistration_Response> getVehicleById(@PathVariable String vehicleId) {
        return Api_Response.<VehicleRegistration_Response>builder()
                .result(vehicleService.getById(vehicleId))
                .build();
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<VehicleRegistration_Response>> getVehiclesByStudent(@PathVariable String studentId) {
        return Api_Response.<List<VehicleRegistration_Response>>builder()
                .result(vehicleService.getByStudentId(studentId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF') or hasAuthority('UTILITY_VIEW')")
    public Api_Response<Page_Response<VehicleRegistration_Response>> getAllVehicles(
            @RequestParam(value = "licensePlate", required = false) String licensePlate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "vehicleType", required = false) String vehicleType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "expiringSoon", required = false) Boolean expiringSoon,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "registrationDate") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<VehicleRegistration_Response>>builder()
                .result(vehicleService.search(licensePlate, keyword, vehicleType, status, expiringSoon, page, size, sortBy, sortDir))
                .build();
    }

    @PutMapping("/{vehicleId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<VehicleRegistration_Response> updateVehicle(
            @PathVariable String vehicleId,
            @RequestBody @Valid VehicleRegistration_Request request
    ) {
        return Api_Response.<VehicleRegistration_Response>builder()
                .result(vehicleService.update(vehicleId, request))
                .build();
    }

    @DeleteMapping("/{vehicleId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<String> deleteVehicle(@PathVariable String vehicleId) {
        vehicleService.delete(vehicleId);
        return Api_Response.<String>builder()
                .result(vehicleId)
                .build();
    }
}
