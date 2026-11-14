package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Room_Request;
import vn.QL_KyTucXa.DTO.Response.Room_Response;
import vn.QL_KyTucXa.Entity.Room;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Room_Mapper {

    @Mapping(target = "roomId", ignore = true)
    @Mapping(target = "floor", ignore = true)
    @Mapping(target = "roomType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Room toEntity(Room_Request request);

    @Mapping(source = "floor.floorId", target = "floorId")
    @Mapping(source = "floor.floorNumber", target = "floorNumber")
    @Mapping(source = "floor.building.buildingId", target = "buildingId")
    @Mapping(source = "floor.building.buildingName", target = "buildingName")
    @Mapping(source = "roomType.roomTypeId", target = "roomTypeId")
    @Mapping(source = "roomType.typeName", target = "roomTypeName")
    Room_Response toResponse(Room entity);

    List<Room_Response> toResponseList(List<Room> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "roomId", ignore = true)
    @Mapping(target = "floor", ignore = true)
    @Mapping(target = "roomType", ignore = true)
    void updateEntityFromRequest(@MappingTarget Room entity, Room_Request request);
}
