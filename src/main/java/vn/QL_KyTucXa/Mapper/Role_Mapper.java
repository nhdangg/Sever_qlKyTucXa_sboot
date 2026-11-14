package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Role_Request;
import vn.QL_KyTucXa.DTO.Response.Role_Response;
import vn.QL_KyTucXa.Entity.Role;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Role_Mapper {
    Role toEntity(Role_Request request);
    Role_Response toResponse(Role entity);
    List<Role_Response> toResponseList(List<Role> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(@MappingTarget Role entity, Role_Request request);
}
