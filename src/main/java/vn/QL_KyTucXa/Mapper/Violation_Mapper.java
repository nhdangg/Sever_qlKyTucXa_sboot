package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Violation_Request;
import vn.QL_KyTucXa.DTO.Response.Violation_Response;
import vn.QL_KyTucXa.Entity.Violation;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Violation_Mapper {

    @Mapping(target = "violationId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "violationType", ignore = true)
    @Mapping(target = "handledBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Violation toEntity(Violation_Request request);

    @Mapping(source = "student.studentId", target = "studentId")
    @Mapping(source = "student.studentCode", target = "studentCode")
    @Mapping(source = "student.fullName", target = "studentFullName")
    @Mapping(source = "violationType.violationTypeId", target = "violationTypeId")
    @Mapping(source = "violationType.violationName", target = "violationName")
    @Mapping(source = "handledBy.staffId", target = "handledByStaffId")
    @Mapping(source = "handledBy.fullName", target = "handledByStaffName")
    Violation_Response toResponse(Violation entity);

    List<Violation_Response> toResponseList(List<Violation> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "violationId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "violationType", ignore = true)
    @Mapping(target = "handledBy", ignore = true)
    void updateEntityFromRequest(@MappingTarget Violation entity, Violation_Request request);
}
