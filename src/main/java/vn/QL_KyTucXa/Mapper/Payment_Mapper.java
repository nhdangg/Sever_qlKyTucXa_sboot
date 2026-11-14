package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Payment_Request;
import vn.QL_KyTucXa.DTO.Response.Payment_Response;
import vn.QL_KyTucXa.Entity.Payment;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Payment_Mapper {

    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "receivedBy", ignore = true)
    @Mapping(target = "paymentDate", ignore = true)
    Payment toEntity(Payment_Request request);

    @Mapping(source = "invoice.invoiceId", target = "invoiceId")
    @Mapping(source = "invoice.invoiceCode", target = "invoiceCode")
    @Mapping(source = "receivedBy.staffId", target = "receivedByStaffId")
    @Mapping(source = "receivedBy.fullName", target = "receivedByStaffName")
    Payment_Response toResponse(Payment entity);

    List<Payment_Response> toResponseList(List<Payment> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "receivedBy", ignore = true)
    void updateEntityFromRequest(@MappingTarget Payment entity, Payment_Request request);
}
