package vn.QL_KyTucXa.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.QL_KyTucXa.DTO.Response.AuditLog_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Entity.AuditLog;
import vn.QL_KyTucXa.Entity.User;
import vn.QL_KyTucXa.Mapper.AuditLog_Mapper;
import vn.QL_KyTucXa.Repository.AuditLog_Repository;
import vn.QL_KyTucXa.Repository.User_Repository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLog_Service {

    AuditLog_Repository auditLogRepository;
    AuditLog_Mapper auditLogMapper;
    User_Repository userRepository;
    CurrentUser_Service currentUserService;
    HttpRequestClientInfoResolver clientInfoResolver;
    ObjectMapper objectMapper;

    // Ghi nhận một hành vi thay đổi dữ liệu vào bảng nhật ký hệ thống độc lập
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAction(String action, String tableName, String recordId, Object oldValueObj, Object newValueObj) {
        try {
            User nguoi_dung_hien_tai = null;
            try {
                String currentUserId = currentUserService.userId();
                if (currentUserId != null) {
                    nguoi_dung_hien_tai = userRepository.findById(currentUserId).orElse(null);
                }
            } catch (Exception ignored) {
                // Có thể là request không cần đăng nhập hoặc unauthenticated
            }

            String oldJson = null;
            if (oldValueObj != null) {
                oldJson = oldValueObj instanceof String s ? s : objectMapper.writeValueAsString(oldValueObj);
            }

            String newJson = null;
            if (newValueObj != null) {
                newJson = newValueObj instanceof String s ? s : objectMapper.writeValueAsString(newValueObj);
            }

            String ip = "127.0.0.1";
            String userAgent = "Unknown";
            try {
                ip = clientInfoResolver.getClientIpAddress();
                userAgent = clientInfoResolver.getClientUserAgent();
                if (userAgent != null && userAgent.length() > 250) {
                    userAgent = userAgent.substring(0, 250);
                }
            } catch (Exception ignored) {
            }

            AuditLog nhat_ky = AuditLog.builder()
                    .user(nguoi_dung_hien_tai)
                    .action(action)
                    .tableName(tableName)
                    .recordId(recordId)
                    .oldValue(oldJson)
                    .newValue(newJson)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(nhat_ky);
            log.debug("[AUDIT] Đã ghi nhật ký: action={}, table={}, recordId={}", action, tableName, recordId);
        } catch (Exception ex) {
            log.error("[AUDIT ERROR] Không thể ghi nhật ký kiểm toán: {}", ex.getMessage());
        }
    }

    // Tra cứu danh sách nhật ký kiểm toán phục vụ Admin xem trên Dashboard
    @Transactional(readOnly = true)
    public Page_Response<AuditLog_Response> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by("createdAt").descending());
        Page<AuditLog> trang_nhat_ky = auditLogRepository.findAll(pageable);

        return Page_Response.<AuditLog_Response>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(trang_nhat_ky.getTotalPages())
                .totalElements(trang_nhat_ky.getTotalElements())
                .data(auditLogMapper.toResponseList(trang_nhat_ky.getContent()))
                .build();
    }
}
