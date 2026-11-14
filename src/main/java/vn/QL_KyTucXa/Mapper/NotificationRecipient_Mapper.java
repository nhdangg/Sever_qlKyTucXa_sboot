package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.NotificationRecipient_Request;
import vn.QL_KyTucXa.DTO.Response.NotificationRecipient_Response;
import vn.QL_KyTucXa.Entity.NotificationRecipient;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationRecipient_Mapper {

    @Mapping(target = "recipientId", ignore = true)
    @Mapping(target = "notification", ignore = true)
    @Mapping(target = "user", ignore = true)
    NotificationRecipient toEntity(NotificationRecipient_Request request);

    @Mapping(source = "notification.notificationId", target = "notificationId")
    @Mapping(source = "notification.title", target = "notificationTitle")
    @Mapping(source = "notification.title", target = "title")
    @Mapping(source = "notification.content", target = "notificationContent")
    @Mapping(source = "notification.content", target = "content")
    @Mapping(source = "notification.targetType", target = "targetType")
    @Mapping(source = "notification.targetId", target = "targetId")
    @Mapping(source = "notification.createdAt", target = "createdAt")
    @Mapping(source = "notification.publishedAt", target = "publishedAt")
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    NotificationRecipient_Response toResponse(NotificationRecipient entity);

    List<NotificationRecipient_Response> toResponseList(List<NotificationRecipient> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "recipientId", ignore = true)
    @Mapping(target = "notification", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateEntityFromRequest(@MappingTarget NotificationRecipient entity, NotificationRecipient_Request request);
}
