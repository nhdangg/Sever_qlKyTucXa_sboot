package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.MaintenanceRequest_Request;
import vn.QL_KyTucXa.DTO.Response.MaintenanceRequest_Response;
import vn.QL_KyTucXa.Entity.MaintenanceRequest;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MaintenanceRequest_Mapper {

    @Mapping(target = "requestId", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "reportedBy", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    @Mapping(target = "requestedDate", ignore = true)
    MaintenanceRequest toEntity(MaintenanceRequest_Request request);

    @Mapping(source = "room.roomId", target = "roomId")
    @Mapping(source = "room.roomNumber", target = "roomNumber")
    @Mapping(source = "reportedBy.userId", target = "reportedByUserId")
    @Mapping(source = "reportedBy.username", target = "reportedByUsername")
    @Mapping(source = "assignedTo.staffId", target = "assignedToStaffId")
    @Mapping(source = "assignedTo.fullName", target = "assignedToStaffName")
    MaintenanceRequest_Response toResponse(MaintenanceRequest entity);

    List<MaintenanceRequest_Response> toResponseList(List<MaintenanceRequest> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "requestId", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "reportedBy", ignore = true)
    @Mapping(target = "assignedTo", ignore = true)
    void updateEntityFromRequest(@MappingTarget MaintenanceRequest entity, MaintenanceRequest_Request request);
}
