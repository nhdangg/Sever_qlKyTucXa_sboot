package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Notification_Request;
import vn.QL_KyTucXa.DTO.Response.Notification_Response;
import vn.QL_KyTucXa.Entity.Notification;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Notification_Mapper {

    @Mapping(target = "notificationId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Notification toEntity(Notification_Request request);

    @Mapping(source = "createdBy.userId", target = "createdByUserId")
    @Mapping(source = "createdBy.username", target = "createdByUsername")
    Notification_Response toResponse(Notification entity);

    List<Notification_Response> toResponseList(List<Notification> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "notificationId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntityFromRequest(@MappingTarget Notification entity, Notification_Request request);
}
