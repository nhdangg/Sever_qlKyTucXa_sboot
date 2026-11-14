package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Feedback_Request;
import vn.QL_KyTucXa.DTO.Response.Feedback_Response;
import vn.QL_KyTucXa.Entity.Feedback;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Feedback_Mapper {

    @Mapping(target = "feedbackId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "response", ignore = true)
    @Mapping(target = "respondedBy", ignore = true)
    @Mapping(target = "respondedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Feedback toEntity(Feedback_Request request);

    @Mapping(source = "student.studentId", target = "studentId")
    @Mapping(source = "student.studentCode", target = "studentCode")
    @Mapping(source = "student.fullName", target = "studentFullName")
    @Mapping(source = "respondedBy.staffId", target = "respondedByStaffId")
    @Mapping(source = "respondedBy.fullName", target = "respondedByStaffName")
    Feedback_Response toResponse(Feedback entity);

    List<Feedback_Response> toResponseList(List<Feedback> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "feedbackId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "response", ignore = true)
    @Mapping(target = "respondedBy", ignore = true)
    @Mapping(target = "respondedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromRequest(@MappingTarget Feedback entity, Feedback_Request request);
}
