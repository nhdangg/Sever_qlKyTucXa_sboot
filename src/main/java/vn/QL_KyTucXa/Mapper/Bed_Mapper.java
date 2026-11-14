package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Bed_Request;
import vn.QL_KyTucXa.DTO.Response.Bed_Response;
import vn.QL_KyTucXa.Entity.Bed;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Bed_Mapper {

    @Mapping(target = "bedId", ignore = true)
    @Mapping(target = "room", ignore = true)
    Bed toEntity(Bed_Request request);

    @Mapping(source = "room.roomId", target = "roomId")
    @Mapping(source = "room.roomNumber", target = "roomNumber")
    @Mapping(source = "room.roomType.roomTypeId", target = "roomTypeId")
    @Mapping(source = "room.roomType.typeName", target = "roomTypeName")
    @Mapping(source = "room.roomType.pricePerMonth", target = "pricePerMonth")
    @Mapping(source = "room.floor.building.buildingId", target = "buildingId")
    @Mapping(source = "room.floor.building.buildingName", target = "buildingName")
    Bed_Response toResponse(Bed entity);

    List<Bed_Response> toResponseList(List<Bed> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "bedId", ignore = true)
    @Mapping(target = "room", ignore = true)
    void updateEntityFromRequest(@MappingTarget Bed entity, Bed_Request request);

    //// bỏ qua đối tương không Mapping kết nối đến đối tuognwj đích
    //@Mapping(source = "user", target = "ok") // mapping kết nối 2 thuộc tính đối tượng nguồn đến đối tượng đích có tên kha nhau
    //tự Mappingkhi các đối tuognwj có tên giống nhau nếu khác nhau sẽ không tự mapping được
    //dùng Denpendency MapStruct
    //@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE) thường sử dugnj cho //update khi request gửi lên mà có giá trị bị null thì sẽ giữ nguyên giá trị cũ và không ghi đè giá trị mới vào
    //@MappingTarget đánh dấu đối tượng chuyền vào chính là đối tượng đích

}
