package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.AuditLog_Request;
import vn.QL_KyTucXa.DTO.Response.AuditLog_Response;
import vn.QL_KyTucXa.Entity.AuditLog;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditLog_Mapper {

    AuditLog toEntity(AuditLog_Request request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    AuditLog_Response toResponse(AuditLog entity);

    List<AuditLog_Response> toResponseList(List<AuditLog> list);
}
