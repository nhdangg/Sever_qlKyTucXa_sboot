package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.ServiceType_Request;
import vn.QL_KyTucXa.DTO.Response.ServiceType_Response;
import vn.QL_KyTucXa.Service.ServiceType_Service;

import java.util.List;

@RestController
@RequestMapping("/api/service-types")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceType_Controller {

    ServiceType_Service serviceTypeService;

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public Api_Response<ServiceType_Response> createServiceType(@RequestBody @Valid ServiceType_Request request) {
        return Api_Response.<ServiceType_Response>builder()
                .result(serviceTypeService.create(request))
                .build();
    }

    @GetMapping("/{serviceTypeId}")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<ServiceType_Response> getServiceTypeById(@PathVariable String serviceTypeId) {
        return Api_Response.<ServiceType_Response>builder()
                .result(serviceTypeService.getById(serviceTypeId))
                .build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<ServiceType_Response>> getAllServiceTypes() {
        return Api_Response.<List<ServiceType_Response>>builder()
                .result(serviceTypeService.getAll())
                .build();
    }

    @PutMapping("/{serviceTypeId}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public Api_Response<ServiceType_Response> updateServiceType(
            @PathVariable String serviceTypeId,
            @RequestBody @Valid ServiceType_Request request
    ) {
        return Api_Response.<ServiceType_Response>builder()
                .result(serviceTypeService.update(serviceTypeId, request))
                .build();
    }

    @DeleteMapping("/{serviceTypeId}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public Api_Response<String> deleteServiceType(@PathVariable String serviceTypeId) {
        serviceTypeService.delete(serviceTypeId);
        return Api_Response.<String>builder()
                .result(serviceTypeId)
                .build();
    }
}
