package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.PasswordResetToken_Request;
import vn.QL_KyTucXa.DTO.Response.PasswordResetToken_Response;
import vn.QL_KyTucXa.Entity.PasswordResetToken;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PasswordResetToken_Mapper {

    PasswordResetToken toEntity(PasswordResetToken_Request request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    PasswordResetToken_Response toResponse(PasswordResetToken entity);

    List<PasswordResetToken_Response> toResponseList(List<PasswordResetToken> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(@MappingTarget PasswordResetToken entity, PasswordResetToken_Request request);
}
