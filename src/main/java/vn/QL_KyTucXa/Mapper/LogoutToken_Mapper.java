package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.LogoutToken_Request;
import vn.QL_KyTucXa.DTO.Response.LogoutToken_Response;
import vn.QL_KyTucXa.Entity.LogoutToken;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LogoutToken_Mapper {

    @Mapping(target = "tokenId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "logoutAt", ignore = true)
    LogoutToken toEntity(LogoutToken_Request request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    LogoutToken_Response toResponse(LogoutToken entity);

    List<LogoutToken_Response> toResponseList(List<LogoutToken> list);
}
