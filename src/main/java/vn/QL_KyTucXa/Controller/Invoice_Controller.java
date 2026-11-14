package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.InvoiceGenerate_Request;
import vn.QL_KyTucXa.DTO.Request.Invoice_Request;
import vn.QL_KyTucXa.DTO.Response.Invoice_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.Invoice_Service;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Invoice_Controller {

    Invoice_Service invoiceService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICE_DRAFT')")
    public Api_Response<Invoice_Response> createInvoice(@RequestBody @Valid Invoice_Request request) {
        return Api_Response.<Invoice_Response>builder()
                .result(invoiceService.create(request))
                .build();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('INVOICE_MANAGE')")
    public Api_Response<List<Invoice_Response>> generateBatchInvoices(@RequestBody @Valid InvoiceGenerate_Request request) {
        return Api_Response.<List<Invoice_Response>>builder()
                .result(invoiceService.generateBatch(request))
                .build();
    }

    @PostMapping("/process-overdue")
    @PreAuthorize("hasAuthority('INVOICE_MANAGE')")
    public Api_Response<Integer> processOverdueInvoices() {
        return Api_Response.<Integer>builder()
                .result(invoiceService.processOverdueInvoices())
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<Invoice_Response>> getMyInvoices() {
        return Api_Response.<List<Invoice_Response>>builder()
                .result(invoiceService.getByStudentId(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('INVOICE_VIEW') or hasRole('STUDENT')")
    public Api_Response<Invoice_Response> getInvoiceById(@PathVariable String invoiceId) {
        return Api_Response.<Invoice_Response>builder()
                .result(invoiceService.getById(invoiceId))
                .build();
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAuthority('INVOICE_VIEW')")
    public Api_Response<List<Invoice_Response>> getInvoicesByStudent(@PathVariable String studentId) {
        return Api_Response.<List<Invoice_Response>>builder()
                .result(invoiceService.getByStudentId(studentId))
                .build();
    }

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("hasAuthority('INVOICE_VIEW')")
    public Api_Response<List<Invoice_Response>> getInvoicesByContract(@PathVariable String contractId) {
        return Api_Response.<List<Invoice_Response>>builder()
                .result(invoiceService.getByContractId(contractId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_VIEW')")
    public Api_Response<Page_Response<Invoice_Response>> getAllInvoices(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "invoiceMonth", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceMonth,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "dueDate") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Invoice_Response>>builder()
                .result(invoiceService.search(keyword, status, invoiceMonth, page, size, sortBy, sortDir))
                .build();
    }

    @PutMapping("/{invoiceId}/issue")
    @PreAuthorize("hasAuthority('INVOICE_MANAGE')")
    public Api_Response<Invoice_Response> issueInvoice(@PathVariable String invoiceId) {
        return Api_Response.<Invoice_Response>builder()
                .result(invoiceService.issue(invoiceId))
                .build();
    }

    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('INVOICE_DRAFT')")
    public Api_Response<Invoice_Response> updateInvoice(
            @PathVariable String invoiceId,
            @RequestBody @Valid Invoice_Request request
    ) {
        return Api_Response.<Invoice_Response>builder()
                .result(invoiceService.update(invoiceId, request))
                .build();
    }

    @PutMapping("/{invoiceId}/cancel")
    @PreAuthorize("hasAuthority('INVOICE_MANAGE')")
    public Api_Response<Invoice_Response> cancelInvoice(
            @PathVariable String invoiceId,
            @RequestParam(defaultValue = "Hủy hóa đơn theo yêu cầu quản lý") String reason
    ) {
        return Api_Response.<Invoice_Response>builder()
                .result(invoiceService.cancel(invoiceId, reason))
                .build();
    }

    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('INVOICE_MANAGE')")
    public Api_Response<String> deleteInvoice(@PathVariable String invoiceId) {
        invoiceService.delete(invoiceId);
        return Api_Response.<String>builder()
                .result(invoiceId)
                .build();
    }
}
