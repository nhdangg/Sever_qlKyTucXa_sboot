package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Staff_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.Staff_Response;
import vn.QL_KyTucXa.Service.Staff_Service;

import java.util.List;

@RestController
@RequestMapping("/api/staffs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Staff_Controller {

    Staff_Service staffService;

    @PostMapping
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public Api_Response<Staff_Response> createStaff(@RequestBody @Valid Staff_Request request) {
        return Api_Response.<Staff_Response>builder()
                .result(staffService.create(request))
                .build();
    }

    @GetMapping("/{staffId}")
    @PreAuthorize("hasAuthority('STAFF_VIEW')")
    public Api_Response<Staff_Response> getStaffById(@PathVariable String staffId) {
        return Api_Response.<Staff_Response>builder()
                .result(staffService.getById(staffId))
                .build();
    }

    @GetMapping("/code/{staffCode}")
    @PreAuthorize("hasAuthority('STAFF_VIEW')")
    public Api_Response<Staff_Response> getStaffByCode(@PathVariable String staffCode) {
        return Api_Response.<Staff_Response>builder()
                .result(staffService.getByStaffCode(staffCode))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_VIEW')")
    public Api_Response<Page_Response<Staff_Response>> getAllStaffs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sortBy", defaultValue = "staffCode") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir
    ) {
        return Api_Response.<Page_Response<Staff_Response>>builder()
                .result(staffService.getAll(page, size, keyword, department, status, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('STAFF_VIEW')")
    public Api_Response<List<Staff_Response>> getAllStaffsWithoutPaging() {
        return Api_Response.<List<Staff_Response>>builder()
                .result(staffService.getAll())
                .build();
    }

    @PutMapping("/{staffId}")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public Api_Response<Staff_Response> updateStaff(
            @PathVariable String staffId,
            @RequestBody @Valid Staff_Request request
    ) {
        return Api_Response.<Staff_Response>builder()
                .result(staffService.update(staffId, request))
                .build();
    }

    @DeleteMapping("/{staffId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Api_Response<String> deleteStaff(@PathVariable String staffId) {
        staffService.delete(staffId);
        return Api_Response.<String>builder()
                .result(staffId)
                .build();
    }
}
