package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Asset_Request;
import vn.QL_KyTucXa.DTO.Response.Asset_Response;
import vn.QL_KyTucXa.Entity.Asset;

import java.util.List;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Asset_Mapper {

    @Mapping(target = "assetId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Asset toEntity(Asset_Request request);

    Asset_Response toResponse(Asset entity);

    List<Asset_Response> toResponseList(List<Asset> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "assetId", ignore = true)
    void updateEntityFromRequest(@MappingTarget Asset entity, Asset_Request request);
}
