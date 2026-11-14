package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Job định kỳ quét và cảnh báo khách thăm ở quá giờ quy định trong KTX
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VisitorScheduler {

    Visitor_Service visitorService;

    // Chạy định kỳ mỗi 30 phút để quét và cảnh báo khách quá giờ quy định (MAX_VISIT_HOUR)
    @Scheduled(cron = "0 0/30 * * * ?")
    public void scheduleOverdueVisitorCheck() {
        try {
            int overdueCount = visitorService.scanAndNotifyOverdueVisitors();
            if (overdueCount > 0) {
                log.info("Đã quét và phát hiện {} khách thăm KTX quá giờ quy định", overdueCount);
            }
        } catch (Exception ex) {
            log.error("Lỗi khi chạy tác vụ kiểm tra khách thăm quá giờ: {}", ex.getMessage(), ex);
        }
    }

    // Tự động đóng các đăng ký khách thăm PENDING tồn đọng vào cuối ngày (23:59 hàng ngày)
    @Scheduled(cron = "0 59 23 * * ?")
    public void scheduleAutoCloseStalePendingVisitors() {
        try {
            visitorService.autoCloseStalePendingVisitors();
        } catch (Exception ex) {
            log.error("Lỗi khi chạy tác vụ tự động đóng đăng ký khách thăm tồn đọng: {}", ex.getMessage(), ex);
        }
    }
}

