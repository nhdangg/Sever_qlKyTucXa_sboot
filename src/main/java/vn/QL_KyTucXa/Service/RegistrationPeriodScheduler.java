package vn.QL_KyTucXa.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Job định kỳ quét và cập nhật trạng thái đợt đăng ký (UPCOMING, OPEN, CLOSED) theo ngày
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RegistrationPeriodScheduler {

    RegistrationPeriod_Service periodService;

    // Tự động quét và đồng bộ trạng thái ngay khi hệ thống vừa khởi động xong
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            int updated = periodService.autoUpdatePeriodStatuses();
            log.info("Khởi động hệ thống: Đã quét và cập nhật trạng thái cho {} đợt đăng ký", updated);
        } catch (Exception e) {
            log.warn("Không thể tự động đồng bộ trạng thái đợt khi khởi động: {}", e.getMessage());
        }
    }

    // Chạy định kỳ lúc 00:05 AM hàng ngày để tự động cập nhật trạng thái các đợt đăng ký
    @Scheduled(cron = "0 5 0 * * ?")
    public void schedulePeriodStatusUpdate() {
        int updated = periodService.autoUpdatePeriodStatuses();
        log.info("Hoàn tất tác vụ quét đợt đăng ký: {} đợt đã cập nhật trạng thái", updated);
    }
}

