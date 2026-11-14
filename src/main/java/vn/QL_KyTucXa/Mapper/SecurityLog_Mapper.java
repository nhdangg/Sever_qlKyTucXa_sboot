package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.SecurityLog_Request;
import vn.QL_KyTucXa.DTO.Response.SecurityLog_Response;
import vn.QL_KyTucXa.Entity.SecurityLog;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SecurityLog_Mapper {

    @Mapping(target = "logId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    @Mapping(target = "eventTime", ignore = true)
    SecurityLog toEntity(SecurityLog_Request request);

    @Mapping(source = "student.studentId", target = "studentId")
    @Mapping(source = "student.studentCode", target = "studentCode")
    @Mapping(source = "student.fullName", target = "studentFullName")
    @Mapping(source = "room.roomId", target = "roomId")
    @Mapping(source = "room.roomNumber", target = "roomNumber")
    @Mapping(source = "recordedBy.staffId", target = "recordedByStaffId")
    @Mapping(source = "recordedBy.fullName", target = "recordedByStaffName")
    SecurityLog_Response toResponse(SecurityLog entity);

    List<SecurityLog_Response> toResponseList(List<SecurityLog> list);
}
