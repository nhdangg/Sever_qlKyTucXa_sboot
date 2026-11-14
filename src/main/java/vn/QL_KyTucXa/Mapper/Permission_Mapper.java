package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Permission_Request;
import vn.QL_KyTucXa.DTO.Response.Permission_Response;
import vn.QL_KyTucXa.Entity.Permission;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Permission_Mapper {
    Permission toEntity(Permission_Request request);
    Permission_Response toResponse(Permission entity);
    List<Permission_Response> toResponseList(List<Permission> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(@MappingTarget Permission entity, Permission_Request request);
}
