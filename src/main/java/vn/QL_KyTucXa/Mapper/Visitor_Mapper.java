package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Visitor_Request;
import vn.QL_KyTucXa.DTO.Response.Visitor_Response;
import vn.QL_KyTucXa.Entity.Visitor;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Visitor_Mapper {

    @Mapping(target = "visitorId", ignore = true)
    @Mapping(target = "hostStudent", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "approver", ignore = true)
    Visitor toEntity(Visitor_Request request);

    @Mapping(source = "hostStudent.studentId", target = "hostStudentId")
    @Mapping(source = "hostStudent.studentCode", target = "hostStudentCode")
    @Mapping(source = "hostStudent.fullName", target = "hostStudentFullName")
    @Mapping(source = "room.roomId", target = "roomId")
    @Mapping(source = "room.roomNumber", target = "roomNumber")
    @Mapping(source = "approver.staffId", target = "approvedByStaffId")
    @Mapping(source = "approver.fullName", target = "approvedByStaffName")
    Visitor_Response toResponse(Visitor entity);

    List<Visitor_Response> toResponseList(List<Visitor> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "visitorId", ignore = true)
    @Mapping(target = "hostStudent", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "approver", ignore = true)
    void updateEntityFromRequest(@MappingTarget Visitor entity, Visitor_Request request);
}
