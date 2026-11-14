package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.SystemSetting_Request;
import vn.QL_KyTucXa.DTO.Response.SystemSetting_Response;
import vn.QL_KyTucXa.Entity.SystemSetting;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SystemSetting_Mapper {

    @Mapping(target = "updatedAt", ignore = true)
    SystemSetting toEntity(SystemSetting_Request request);

    SystemSetting_Response toResponse(SystemSetting entity);

    List<SystemSetting_Response> toResponseList(List<SystemSetting> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "settingKey", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(@MappingTarget SystemSetting entity, SystemSetting_Request request);
}
