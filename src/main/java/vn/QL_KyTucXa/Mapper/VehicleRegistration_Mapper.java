package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.VehicleRegistration_Request;
import vn.QL_KyTucXa.DTO.Response.VehicleRegistration_Response;
import vn.QL_KyTucXa.Entity.VehicleRegistration;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VehicleRegistration_Mapper {

    @Mapping(target = "vehicleId", ignore = true)
    @Mapping(target = "student", ignore = true)
    VehicleRegistration toEntity(VehicleRegistration_Request request);

    @Mapping(source = "student.studentId", target = "studentId")
    @Mapping(source = "student.studentCode", target = "studentCode")
    @Mapping(source = "student.fullName", target = "studentFullName")
    VehicleRegistration_Response toResponse(VehicleRegistration entity);

    List<VehicleRegistration_Response> toResponseList(List<VehicleRegistration> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "vehicleId", ignore = true)
    @Mapping(target = "student", ignore = true)
    void updateEntityFromRequest(@MappingTarget VehicleRegistration entity, VehicleRegistration_Request request);
}
