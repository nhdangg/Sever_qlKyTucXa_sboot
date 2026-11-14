package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.RoomTransfer_Request;
import vn.QL_KyTucXa.DTO.Response.RoomTransfer_Response;
import vn.QL_KyTucXa.Entity.RoomTransfer;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoomTransfer_Mapper {

    @Mapping(target = "transferId", ignore = true)
    @Mapping(target = "contract", ignore = true)
    @Mapping(target = "oldBed", ignore = true)
    @Mapping(target = "newBed", ignore = true)
    @Mapping(target = "approver", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    RoomTransfer toEntity(RoomTransfer_Request request);

    @Mapping(source = "contract.contractId", target = "contractId")
    @Mapping(source = "contract.contractCode", target = "contractCode")
    @Mapping(source = "contract.student.studentId", target = "studentId")
    @Mapping(source = "contract.student.fullName", target = "studentFullName")
    @Mapping(source = "oldBed.bedId", target = "oldBedId")
    @Mapping(source = "oldBed.bedNumber", target = "oldBedNumber")
    @Mapping(source = "oldBed.room.roomNumber", target = "oldRoomNumber")
    @Mapping(source = "newBed.bedId", target = "newBedId")
    @Mapping(source = "newBed.bedNumber", target = "newBedNumber")
    @Mapping(source = "newBed.room.roomNumber", target = "newRoomNumber")
    @Mapping(source = "approver.staffId", target = "approvedByStaffId")
    @Mapping(source = "approver.fullName", target = "approvedByStaffName")
    RoomTransfer_Response toResponse(RoomTransfer entity);

    List<RoomTransfer_Response> toResponseList(List<RoomTransfer> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "transferId", ignore = true)
    @Mapping(target = "contract", ignore = true)
    @Mapping(target = "oldBed", ignore = true)
    @Mapping(target = "newBed", ignore = true)
    @Mapping(target = "approver", ignore = true)
    void updateEntityFromRequest(@MappingTarget RoomTransfer entity, RoomTransfer_Request request);
}
