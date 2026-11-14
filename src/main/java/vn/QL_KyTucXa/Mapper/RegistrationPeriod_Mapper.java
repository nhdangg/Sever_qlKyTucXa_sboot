package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.RegistrationPeriod_Request;
import vn.QL_KyTucXa.DTO.Response.RegistrationPeriod_Response;
import vn.QL_KyTucXa.Entity.RegistrationPeriod;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RegistrationPeriod_Mapper {

    @Mapping(target = "periodId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    RegistrationPeriod toEntity(RegistrationPeriod_Request request);

    RegistrationPeriod_Response toResponse(RegistrationPeriod entity);

    List<RegistrationPeriod_Response> toResponseList(List<RegistrationPeriod> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "periodId", ignore = true)
    void updateEntityFromRequest(@MappingTarget RegistrationPeriod entity, RegistrationPeriod_Request request);
}
