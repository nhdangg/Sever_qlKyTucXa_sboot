package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.ViolationType_Request;
import vn.QL_KyTucXa.DTO.Response.ViolationType_Response;
import vn.QL_KyTucXa.Service.ViolationType_Service;

import java.util.List;

@RestController
@RequestMapping("/api/violation-types")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViolationType_Controller {

    ViolationType_Service violationTypeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('CATALOG_MANAGE')")
    public Api_Response<ViolationType_Response> createViolationType(@RequestBody @Valid ViolationType_Request request) {
        return Api_Response.<ViolationType_Response>builder()
                .result(violationTypeService.create(request))
                .build();
    }

    @GetMapping("/{violationTypeId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<ViolationType_Response> getViolationTypeById(@PathVariable String violationTypeId) {
        return Api_Response.<ViolationType_Response>builder()
                .result(violationTypeService.getById(violationTypeId))
                .build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<ViolationType_Response>> getAllViolationTypes() {
        return Api_Response.<List<ViolationType_Response>>builder()
                .result(violationTypeService.getAll())
                .build();
    }

    @GetMapping("/severity/{severityLevel}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<ViolationType_Response>> getViolationTypesBySeverity(@PathVariable String severityLevel) {
        return Api_Response.<List<ViolationType_Response>>builder()
                .result(violationTypeService.getBySeverityLevel(severityLevel))
                .build();
    }

    @PutMapping("/{violationTypeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('CATALOG_MANAGE')")
    public Api_Response<ViolationType_Response> updateViolationType(
            @PathVariable String violationTypeId,
            @RequestBody @Valid ViolationType_Request request
    ) {
        return Api_Response.<ViolationType_Response>builder()
                .result(violationTypeService.update(violationTypeId, request))
                .build();
    }

    @DeleteMapping("/{violationTypeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('CATALOG_MANAGE')")
    public Api_Response<String> deleteViolationType(@PathVariable String violationTypeId) {
        violationTypeService.delete(violationTypeId);
        return Api_Response.<String>builder()
                .result(violationTypeId)
                .build();
    }
}
