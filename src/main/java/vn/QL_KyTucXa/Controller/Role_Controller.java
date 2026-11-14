package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Role_Request;
import vn.QL_KyTucXa.DTO.Response.Role_Response;
import vn.QL_KyTucXa.Service.Role_Service;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Role_Controller {

    Role_Service roleService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<Role_Response> createRole(@RequestBody @Valid Role_Request request) {
        return Api_Response.<Role_Response>builder()
                .result(roleService.create(request))
                .build();
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAnyAuthority('RBAC_VIEW', 'ROLE_ADMIN')")
    public Api_Response<Role_Response> getRoleById(@PathVariable String roleId) {
        return Api_Response.<Role_Response>builder()
                .result(roleService.getById(roleId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('RBAC_VIEW', 'ROLE_ADMIN')")
    public Api_Response<List<Role_Response>> getAllRoles() {
        return Api_Response.<List<Role_Response>>builder()
                .result(roleService.getAll())
                .build();
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<Role_Response> updateRole(
            @PathVariable String roleId,
            @RequestBody @Valid Role_Request request
    ) {
        return Api_Response.<Role_Response>builder()
                .result(roleService.update(roleId, request))
                .build();
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAnyAuthority('RBAC_MANAGE', 'ROLE_ADMIN')")
    public Api_Response<String> deleteRole(@PathVariable String roleId) {
        roleService.delete(roleId);
        return Api_Response.<String>builder()
                .result(roleId)
                .build();
    }
}
