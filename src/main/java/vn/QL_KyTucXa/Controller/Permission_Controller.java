package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Permission_Request;
import vn.QL_KyTucXa.DTO.Response.Permission_Response;
import vn.QL_KyTucXa.Service.Permission_Service;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Permission_Controller {

    Permission_Service permissionService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<Permission_Response> createPermission(@RequestBody @Valid Permission_Request request) {
        return Api_Response.<Permission_Response>builder()
                .result(permissionService.create(request))
                .build();
    }

    @GetMapping("/{permissionId}")
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<Permission_Response> getPermissionById(@PathVariable String permissionId) {
        return Api_Response.<Permission_Response>builder()
                .result(permissionService.getById(permissionId))
                .build();
    }

    @GetMapping("/module/{module}")
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<List<Permission_Response>> getPermissionsByModule(@PathVariable String module) {
        return Api_Response.<List<Permission_Response>>builder()
                .result(permissionService.getByModule(module))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'RBAC_VIEW', 'ROLE_ADMIN')")
    public Api_Response<List<Permission_Response>> getAllPermissions() {
        return Api_Response.<List<Permission_Response>>builder()
                .result(permissionService.getAll())
                .build();
    }

    @PutMapping("/{permissionId}")
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<Permission_Response> updatePermission(
            @PathVariable String permissionId,
            @RequestBody @Valid Permission_Request request
    ) {
        return Api_Response.<Permission_Response>builder()
                .result(permissionService.update(permissionId, request))
                .build();
    }

    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<String> deletePermission(@PathVariable String permissionId) {
        permissionService.delete(permissionId);
        return Api_Response.<String>builder()
                .result(permissionId)
                .build();
    }
}
