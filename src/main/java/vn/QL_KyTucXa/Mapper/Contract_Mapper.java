package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Contract_Request;
import vn.QL_KyTucXa.DTO.Response.Contract_Response;
import vn.QL_KyTucXa.Entity.Contract;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Contract_Mapper {

    @Mapping(target = "contractId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "bed", ignore = true)
    @Mapping(target = "period", ignore = true)
    @Mapping(target = "approver", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "signedDate", ignore = true)
    @Mapping(target = "terminatedDate", ignore = true)
    @Mapping(target = "terminationReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Contract toEntity(Contract_Request request);

    @Mapping(source = "student.studentId", target = "studentId")
    @Mapping(source = "student.studentCode", target = "studentCode")
    @Mapping(source = "student.fullName", target = "studentFullName")
    @Mapping(source = "bed.bedId", target = "bedId")
    @Mapping(source = "bed.bedNumber", target = "bedNumber")
    @Mapping(source = "bed.room.roomId", target = "roomId")
    @Mapping(source = "bed.room.roomNumber", target = "roomNumber")
    @Mapping(source = "period.periodId", target = "periodId")
    @Mapping(source = "period.periodName", target = "periodName")
    @Mapping(source = "approver.staffId", target = "approvedByStaffId")
    @Mapping(source = "approver.fullName", target = "approvedByStaffName")
    Contract_Response toResponse(Contract entity);

    List<Contract_Response> toResponseList(List<Contract> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "contractId", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "bed", ignore = true)
    @Mapping(target = "period", ignore = true)
    @Mapping(target = "approver", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "signedDate", ignore = true)
    @Mapping(target = "terminatedDate", ignore = true)
    @Mapping(target = "terminationReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(@MappingTarget Contract entity, Contract_Request request);
}
