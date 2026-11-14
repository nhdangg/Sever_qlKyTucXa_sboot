package vn.QL_KyTucXa.Controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Request.RoomTransfer_Request;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.DTO.Response.RoomTransfer_Response;
import vn.QL_KyTucXa.Service.CurrentUser_Service;
import vn.QL_KyTucXa.Service.RoomTransfer_Service;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/room-transfers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomTransfer_Controller {

    RoomTransfer_Service roomTransferService;
    CurrentUser_Service currentUserService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'STUDENT')")
    public Api_Response<RoomTransfer_Response> requestTransfer(@RequestBody @Valid RoomTransfer_Request request) {
        if (currentUserService.hasRole("STUDENT")) {
            return Api_Response.<RoomTransfer_Response>builder()
                    .result(roomTransferService.createForStudent(currentUserService.studentId(), request))
                    .build();
        }
        return Api_Response.<RoomTransfer_Response>builder()
                .result(roomTransferService.create(request))
                .build();
    }

    @GetMapping({"/my", "/me"})
    @PreAuthorize("isAuthenticated()")
    public Api_Response<List<RoomTransfer_Response>> getMyTransfers() {
        return Api_Response.<List<RoomTransfer_Response>>builder()
                .result(roomTransferService.getByStudentId(currentUserService.studentId()))
                .build();
    }

    @GetMapping("/{transferId}")
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<RoomTransfer_Response> getTransferById(@PathVariable String transferId) {
        return Api_Response.<RoomTransfer_Response>builder()
                .result(roomTransferService.getById(transferId))
                .build();
    }

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<List<RoomTransfer_Response>> getTransfersByContract(@PathVariable String contractId) {
        return Api_Response.<List<RoomTransfer_Response>>builder()
                .result(roomTransferService.getByContractId(contractId))
                .build();
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<List<RoomTransfer_Response>> getTransfersByStudent(@PathVariable String studentId) {
        return Api_Response.<List<RoomTransfer_Response>>builder()
                .result(roomTransferService.getByStudentId(studentId))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTRACT_VIEW')")
    public Api_Response<Page_Response<RoomTransfer_Response>> getAllTransfers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "student", required = false) String student,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir
    ) {
        Page_Response<RoomTransfer_Response> result;
        if (status == null && student == null && fromDate == null && toDate == null) {
            result = roomTransferService.getAll(page, size, sortBy, sortDir);
        } else {
            result = roomTransferService.getAll(page, size, status, student, fromDate, toDate, sortBy, sortDir);
        }
        return Api_Response.<Page_Response<RoomTransfer_Response>>builder()
                .result(result)
                .build();
    }

    @PutMapping("/{transferId}/approve")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<RoomTransfer_Response> approveTransfer(
            @PathVariable String transferId,
            @RequestParam(required = false) String staffId
    ) {
        return Api_Response.<RoomTransfer_Response>builder()
                .result(roomTransferService.approve(transferId, staffId))
                .build();
    }

    @PutMapping("/{transferId}/reject")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<RoomTransfer_Response> rejectTransfer(
            @PathVariable String transferId,
            @RequestParam(required = false) String staffId,
            @RequestParam(defaultValue = "Không đáp ứng yêu cầu chuyển phòng") String reason
    ) {
        return Api_Response.<RoomTransfer_Response>builder()
                .result(roomTransferService.reject(transferId, staffId, reason))
                .build();
    }

    @PutMapping("/{transferId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<RoomTransfer_Response> cancelTransfer(@PathVariable String transferId) {
        String studentId = currentUserService.hasRole("STUDENT") ? currentUserService.studentId() : null;
        return Api_Response.<RoomTransfer_Response>builder()
                .result(roomTransferService.cancel(transferId, studentId))
                .build();
    }

    @DeleteMapping("/{transferId}")
    @PreAuthorize("hasAuthority('CONTRACT_MANAGE')")
    public Api_Response<String> deleteTransfer(@PathVariable String transferId) {
        roomTransferService.delete(transferId);
        return Api_Response.<String>builder()
                .result(transferId)
                .build();
    }
}
