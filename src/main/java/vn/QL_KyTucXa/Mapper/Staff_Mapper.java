package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Staff_Request;
import vn.QL_KyTucXa.DTO.Response.Staff_Response;
import vn.QL_KyTucXa.Entity.Staff;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Staff_Mapper {

    @Mapping(target = "staffId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Staff toEntity(Staff_Request request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    Staff_Response toResponse(Staff entity);

    List<Staff_Response> toResponseList(List<Staff> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "staffId", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateEntityFromRequest(@MappingTarget Staff entity, Staff_Request request);
}
