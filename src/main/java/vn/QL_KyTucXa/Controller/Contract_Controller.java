package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.Contract_Request;
import vn.QL_KyTucXa.DTO.Request.StudentContractApply_Request;
import vn.QL_KyTucXa.DTO.Response.Contract_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.Contract_Service;
import vn.QL_KyTucXa.Service.CurrentUser_Service;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Contract_Controller {

    Contract_Service contractService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('CONTRACT_DRAFT')")
    public Api_Response<Contract_Response> createContract(@RequestBody @Valid Contract_Request request) {
        return Api_Response.<Contract_Response>builder()
                .result(contractService.create(request))
                .build();
    }

    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Contract_Response> applyOnline(@RequestBody @Valid StudentContractApply_Request request) {
        return Api_Response.<Contract_Response>builder()
                .result(contractService.applyOnline(currentUserService.studentId(), request))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<Contract_Response>> getMyContracts() {
        return Api_Response.<List<Contract_Response>>builder()
                .result(contractService.getByStudentId(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/{contractId}")
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<Contract_Response> getContractById(@PathVariable String contractId) {
        return Api_Response.<Contract_Response>builder()
                .result(contractService.getById(contractId))
                .build();
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<List<Contract_Response>> getContractsByStudent(@PathVariable String studentId) {
        return Api_Response.<List<Contract_Response>>builder()
                .result(contractService.getByStudentId(studentId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<Page_Response<Contract_Response>> getAllContracts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "periodId", required = false) String periodId,
            @RequestParam(value = "buildingId", required = false) String buildingId,
            @RequestParam(value = "roomId", required = false) String roomId,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        return Api_Response.<Page_Response<Contract_Response>>builder()
                .result(contractService.getAll(page, size, keyword, status, periodId, buildingId, roomId, sortBy, sortDir))
                .build();
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<List<Contract_Response>> getAllContractsWithoutPaging() {
        return Api_Response.<List<Contract_Response>>builder()
                .result(contractService.getAll())
                .build();
    }

    @PutMapping("/{contractId}/approve")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<Contract_Response> approveContract(
            @PathVariable String contractId,
            @RequestParam(required = false) String staffId
    ) {
        String effectiveStaffId = (staffId != null && !staffId.isBlank())
                ? staffId
                : currentUserService.findStaffId().orElse(null);
        return Api_Response.<Contract_Response>builder()
                .result(contractService.approve(contractId, effectiveStaffId))
                .build();
    }

    @PutMapping("/{contractId}/terminate")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<Contract_Response> terminateContract(
            @PathVariable String contractId,
            @RequestParam(defaultValue = "Kết thúc hợp đồng") String reason
    ) {
        return Api_Response.<Contract_Response>builder()
                .result(contractService.terminate(contractId, reason))
                .build();
    }

    @PutMapping("/{contractId}/reject")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<Contract_Response> rejectContract(
            @PathVariable String contractId,
            @RequestParam(required = false) String staffId,
            @RequestParam(defaultValue = "Từ chối duyệt đơn đăng ký") String reason
    ) {
        String effectiveStaffId = (staffId != null && !staffId.isBlank())
                ? staffId
                : currentUserService.findStaffId().orElse(null);
        return Api_Response.<Contract_Response>builder()
                .result(contractService.reject(contractId, effectiveStaffId, reason))
                .build();
    }

    @PutMapping("/{contractId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Contract_Response> cancelContract(
            @PathVariable String contractId,
            @RequestParam(defaultValue = "Sinh viên hủy đơn đăng ký") String reason
    ) {
        return Api_Response.<Contract_Response>builder()
                .result(contractService.cancel(contractId, currentUserService.studentId(), reason))
                .build();
    }

    @PutMapping("/{contractId}")
    @PreAuthorize("hasAuthority('CONTRACT_DRAFT')")
    public Api_Response<Contract_Response> updateContract(
            @PathVariable String contractId,
            @RequestBody @Valid Contract_Request request
    ) {
        return Api_Response.<Contract_Response>builder()
                .result(contractService.update(contractId, request))
                .build();
    }

    @DeleteMapping("/{contractId}")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<String> deleteContract(@PathVariable String contractId) {
        contractService.delete(contractId);
        return Api_Response.<String>builder()
                .result(contractId)
                .build();
    }
}
