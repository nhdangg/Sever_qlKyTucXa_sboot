package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Student_Request;
import vn.QL_KyTucXa.DTO.Response.Student_Response;
import vn.QL_KyTucXa.Entity.Student;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Student_Mapper {

    @Mapping(target = "studentId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Student toEntity(Student_Request request);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.username", target = "username")
    Student_Response toResponse(Student entity);

    List<Student_Response> toResponseList(List<Student> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "studentId", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateEntityFromRequest(@MappingTarget Student entity, Student_Request request);

}
