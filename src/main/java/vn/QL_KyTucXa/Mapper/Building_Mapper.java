package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Building_Request;
import vn.QL_KyTucXa.DTO.Response.Building_Response;
import vn.QL_KyTucXa.Entity.Building;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Building_Mapper {

    @Mapping(target = "buildingId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Building toEntity(Building_Request request);

    Building_Response toResponse(Building entity);

    List<Building_Response> toResponseList(List<Building> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "buildingId", ignore = true)
    void updateEntityFromRequest(@MappingTarget Building entity, Building_Request request);
}
