package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.Invoice_Request;
import vn.QL_KyTucXa.DTO.Response.Invoice_Response;
import vn.QL_KyTucXa.Entity.Invoice;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {InvoiceItem_Mapper.class},
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface Invoice_Mapper {

    @Mapping(target = "invoiceId", ignore = true)
    @Mapping(target = "contract", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "invoiceItems", ignore = true)
    Invoice toEntity(Invoice_Request request);

    @Mapping(source = "contract.contractId", target = "contractId")
    @Mapping(source = "contract.contractCode", target = "contractCode")
    @Mapping(source = "student.studentId", target = "studentId")
    @Mapping(source = "student.studentCode", target = "studentCode")
    @Mapping(source = "student.fullName", target = "studentFullName")
    @Mapping(source = "invoiceItems", target = "invoiceItems")
    Invoice_Response toResponse(Invoice entity);

    List<Invoice_Response> toResponseList(List<Invoice> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "invoiceId", ignore = true)
    @Mapping(target = "invoiceCode", ignore = true)
    @Mapping(target = "contract", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "paidAmount", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "invoiceItems", ignore = true)
    void updateEntityFromRequest(@MappingTarget Invoice entity, Invoice_Request request);
}
