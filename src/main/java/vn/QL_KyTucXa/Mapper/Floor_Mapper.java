package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Floor_Request;
import vn.QL_KyTucXa.DTO.Response.Floor_Response;
import vn.QL_KyTucXa.Entity.Floor;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Floor_Mapper {

    @Mapping(target = "floorId", ignore = true)
    @Mapping(target = "building", ignore = true)
    Floor toEntity(Floor_Request request);

    @Mapping(source = "building.buildingId", target = "buildingId")
    @Mapping(source = "building.buildingName", target = "buildingName")
    Floor_Response toResponse(Floor entity);

    List<Floor_Response> toResponseList(List<Floor> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "floorId", ignore = true)
    @Mapping(target = "building", ignore = true)
    void updateEntityFromRequest(@MappingTarget Floor entity, Floor_Request request);
}
