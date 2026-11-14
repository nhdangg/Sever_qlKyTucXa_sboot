package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.ServiceType_Request;
import vn.QL_KyTucXa.DTO.Response.ServiceType_Response;
import vn.QL_KyTucXa.Entity.ServiceType;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ServiceType_Mapper {

    @Mapping(target = "serviceTypeId", ignore = true)
    ServiceType toEntity(ServiceType_Request request);

    ServiceType_Response toResponse(ServiceType entity);

    List<ServiceType_Response> toResponseList(List<ServiceType> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "serviceTypeId", ignore = true)
    void updateEntityFromRequest(@MappingTarget ServiceType entity, ServiceType_Request request);
}
