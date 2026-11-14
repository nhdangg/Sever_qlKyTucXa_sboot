package vn.QL_KyTucXa.Controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.QL_KyTucXa.DTO.Api_Response;
import vn.QL_KyTucXa.DTO.Response.NotificationRecipient_Response;
import vn.QL_KyTucXa.DTO.Response.Page_Response;
import vn.QL_KyTucXa.Service.NotificationRecipient_Service;

@RestController
@RequestMapping("/api/notifications/inbox")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationRecipient_Controller {

    NotificationRecipient_Service recipientService;

    // Xem hộp thư thông báo của cá nhân đang đăng nhập có phân trang
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Page_Response<NotificationRecipient_Response>> getMyInbox(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "isRead", required = false) Boolean isRead
    ) {
        return Api_Response.<Page_Response<NotificationRecipient_Response>>builder()
                .result(recipientService.getMyNotifications(page, size, isRead))
                .build();
    }

    // Đánh dấu một thông báo là đã đọc
    @PutMapping("/{recipientId}/read")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<NotificationRecipient_Response> markAsRead(@PathVariable String recipientId) {
        return Api_Response.<NotificationRecipient_Response>builder()
                .result(recipientService.markAsRead(recipientId))
                .build();
    }

    // Đánh dấu tất cả thông báo trong hộp thư là đã đọc
    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<String> markAllAsRead() {
        recipientService.markAllAsRead();
        return Api_Response.<String>builder()
                .result("Đã đánh dấu tất cả thông báo là đã đọc")
                .build();
    }

    // Lấy số lượng thông báo chưa đọc
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Api_Response<Long> getUnreadCount() {
        return Api_Response.<Long>builder()
                .result(recipientService.getUnreadCount())
                .build();
    }
}

