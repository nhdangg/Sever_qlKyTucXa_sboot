package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.ViolationType_Request;
import vn.QL_KyTucXa.DTO.Response.ViolationType_Response;
import vn.QL_KyTucXa.Entity.ViolationType;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ViolationType_Mapper {

    @Mapping(target = "violationTypeId", ignore = true)
    ViolationType toEntity(ViolationType_Request request);

    ViolationType_Response toResponse(ViolationType entity);

    List<ViolationType_Response> toResponseList(List<ViolationType> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "violationTypeId", ignore = true)
    void updateEntityFromRequest(@MappingTarget ViolationType entity, ViolationType_Request request);
}
