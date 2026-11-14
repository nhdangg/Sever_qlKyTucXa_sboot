package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.RoomAsset_Request;
import vn.QL_KyTucXa.DTO.Response.RoomAsset_Response;
import vn.QL_KyTucXa.Entity.RoomAsset;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoomAsset_Mapper {

    @Mapping(target = "roomAssetId", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "asset", ignore = true)
    RoomAsset toEntity(RoomAsset_Request request);

    @Mapping(source = "room.roomId", target = "roomId")
    @Mapping(source = "room.roomNumber", target = "roomNumber")
    @Mapping(source = "asset.assetId", target = "assetId")
    @Mapping(source = "asset.assetCode", target = "assetCode")
    @Mapping(source = "asset.assetName", target = "assetName")
    RoomAsset_Response toResponse(RoomAsset entity);

    List<RoomAsset_Response> toResponseList(List<RoomAsset> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "roomAssetId", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "asset", ignore = true)
    void updateEntityFromRequest(@MappingTarget RoomAsset entity, RoomAsset_Request request);
}
