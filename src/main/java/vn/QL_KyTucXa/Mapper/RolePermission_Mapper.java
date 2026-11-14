package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.RolePermission_Request;
import vn.QL_KyTucXa.DTO.Response.RolePermission_Response;
import vn.QL_KyTucXa.Entity.RolePermission;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RolePermission_Mapper {

    RolePermission toEntity(RolePermission_Request request);

    @Mapping(source = "role.roleId", target = "roleId")
    @Mapping(source = "role.roleName", target = "roleName")
    @Mapping(source = "role.roleCode", target = "roleCode")
    @Mapping(source = "permission.permissionId", target = "permissionId")
    @Mapping(source = "permission.permissionCode", target = "permissionCode")
    @Mapping(source = "permission.module", target = "module")
    RolePermission_Response toResponse(RolePermission entity);

    List<RolePermission_Response> toResponseList(List<RolePermission> list);

//    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
//    void updateEntityFromRequest(@MappingTarget RolePermission entity, RolePermission_Request request);
}
