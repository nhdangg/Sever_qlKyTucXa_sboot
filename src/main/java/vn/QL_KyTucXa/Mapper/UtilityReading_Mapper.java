package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.UtilityReading_Request;
import vn.QL_KyTucXa.DTO.Response.UtilityReading_Response;
import vn.QL_KyTucXa.Entity.UtilityReading;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UtilityReading_Mapper {

    @Mapping(target = "readingId", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "serviceType", ignore = true)
    @Mapping(target = "consumption", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    @Mapping(target = "recordedAt", ignore = true)
    UtilityReading toEntity(UtilityReading_Request request);

    @Mapping(source = "room.roomId", target = "roomId")
    @Mapping(source = "room.roomNumber", target = "roomNumber")
    @Mapping(source = "serviceType.serviceTypeId", target = "serviceTypeId")
    @Mapping(source = "serviceType.serviceName", target = "serviceName")
    @Mapping(source = "recordedBy.staffId", target = "recordedByStaffId")
    @Mapping(source = "recordedBy.fullName", target = "recordedByStaffName")
    UtilityReading_Response toResponse(UtilityReading entity);

    List<UtilityReading_Response> toResponseList(List<UtilityReading> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "readingId", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "serviceType", ignore = true)
    @Mapping(target = "consumption", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    void updateEntityFromRequest(@MappingTarget UtilityReading entity, UtilityReading_Request request);
}
