package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.RefreshToken_Request;
import vn.QL_KyTucXa.DTO.Response.RefreshToken_Response;
import vn.QL_KyTucXa.Entity.RefreshToken;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RefreshToken_Mapper {

    RefreshToken toEntity(RefreshToken_Request request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    RefreshToken_Response toResponse(RefreshToken entity);

    List<RefreshToken_Response> toResponseList(List<RefreshToken> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(@MappingTarget RefreshToken entity, RefreshToken_Request request);
}
