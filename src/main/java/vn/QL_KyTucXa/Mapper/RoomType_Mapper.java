package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.RoomType_Request;
import vn.QL_KyTucXa.DTO.Response.RoomType_Response;
import vn.QL_KyTucXa.Entity.RoomType;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoomType_Mapper {

    @Mapping(target = "roomTypeId", ignore = true)
    RoomType toEntity(RoomType_Request request);

    RoomType_Response toResponse(RoomType entity);

    List<RoomType_Response> toResponseList(List<RoomType> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "roomTypeId", ignore = true)
    void updateEntityFromRequest(@MappingTarget RoomType entity, RoomType_Request request);
}
