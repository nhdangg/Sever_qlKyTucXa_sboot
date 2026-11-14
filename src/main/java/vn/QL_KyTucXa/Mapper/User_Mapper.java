package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.User_Request;
import vn.QL_KyTucXa.DTO.Response.User_Response;
import vn.QL_KyTucXa.Entity.User;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {Role_Mapper.class},
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface User_Mapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(User_Request request);

    @Mapping(source = "role", target = "role")
    User_Response toResponse(User entity);

    List<User_Response> toResponseList(List<User> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntityFromRequest(@MappingTarget User entity, User_Request request);
}
