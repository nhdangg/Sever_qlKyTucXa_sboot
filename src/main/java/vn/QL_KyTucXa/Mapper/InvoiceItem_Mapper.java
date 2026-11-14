package vn.QL_KyTucXa.Mapper;

import org.mapstruct.*;
import vn.QL_KyTucXa.DTO.Request.InvoiceItem_Request;
import vn.QL_KyTucXa.DTO.Response.InvoiceItem_Response;
import vn.QL_KyTucXa.Entity.InvoiceItem;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InvoiceItem_Mapper {

    @Mapping(target = "itemId", ignore = true)
    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "amount", ignore = true)
    InvoiceItem toEntity(InvoiceItem_Request request);

    @Mapping(source = "invoice.invoiceId", target = "invoiceId")
    InvoiceItem_Response toResponse(InvoiceItem entity);

    List<InvoiceItem_Response> toResponseList(List<InvoiceItem> list);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "itemId", ignore = true)
    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "amount", ignore = true)
    void updateEntityFromRequest(@MappingTarget InvoiceItem entity, InvoiceItem_Request request);
}
