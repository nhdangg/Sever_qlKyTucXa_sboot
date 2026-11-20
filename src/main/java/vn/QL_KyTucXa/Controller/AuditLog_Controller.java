package vn.QL_KyTucXa.Controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Response.AuditLog_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.AuditLog_Service;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLog_Controller {

    AuditLog_Service auditLogService;

    /**
     * API tra cứu danh sách nhật ký kiểm toán hệ thống
     * Chỉ tài khoản có vai trò ADMIN hoặc quyền AUDIT_VIEW mới được phép xem
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AUDIT_VIEW')")
    public Api_Response<Page_Response<AuditLog_Response>> getAllLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Api_Response.<Page_Response<AuditLog_Response>>builder()
                .result(auditLogService.getAllLogs(page, size))
                .build();
    }
}
